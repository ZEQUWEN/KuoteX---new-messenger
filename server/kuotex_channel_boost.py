"""
KuoteX Messenger - Backend Module for Phase 2: Channel Boosting Logic & Privilege Validation
Implements custom MTProto architecture validation and Google Cloud Firestore ACID transactions.
"""

from __future__ import annotations
import math
import time
import uuid
from typing import Dict, Any, Tuple, Optional
from google.cloud import firestore
from google.cloud.firestore_v1.transaction import Transaction


# --- Custom Domain Exceptions ---

class BoostSecurityException(Exception):
    """Base exception for boost security and permission violations."""
    def __init__(self, code: int, message: str):
        super().__init__(message)
        self.code = code
        self.message = message


class UnauthorizedBoostError(BoostSecurityException):
    """Raised when non-VIP, non-admin user attempts to boost a channel."""
    def __init__(self, message: str = "VIP subscription or Admin/Developer role required to boost channels."):
        super().__init__(403, message)


class InsufficientBoostVotesError(BoostSecurityException):
    """Raised when user has exhausted all available boost votes."""
    def __init__(self, available: int, requested: int):
        super().__init__(400, f"Insufficient boost votes: requested {requested}, but only {available} available.")


class ChannelNotFoundError(BoostSecurityException):
    def __init__(self, channel_id: str):
        super().__init__(404, f"Channel with ID '{channel_id}' not found.")


class UserNotFoundError(BoostSecurityException):
    def __init__(self, user_id: str):
        super().__init__(404, f"User with ID '{user_id}' not found.")


# --- Mathematical Level Progression Table (Exponential MTProto Curve) ---

LEVEL_THRESHOLDS: Dict[int, int] = {
    1: 5,
    2: 10,
    3: 20,
    4: 35,
    5: 55,
    6: 85,
    7: 130,
    8: 190,
    9: 270,
    10: 400
}


def compute_channel_level_from_votes(total_votes: int) -> Tuple[int, int]:
    """
    Computes (level, next_level_required_votes) from raw vote count.
    Level 1: 5 boosts
    Level 2: 10 boosts
    Level 3: 20 boosts
    Level 4: 35 boosts
    Scaling up to Level 10 exponentially.
    """
    level = 0
    for lvl, req_votes in sorted(LEVEL_THRESHOLDS.items()):
        if total_votes >= req_votes:
            level = lvl
        else:
            break
            
    next_req = LEVEL_THRESHOLDS.get(min(level + 1, 10), 400)
    return min(level, 10), next_req


def calculate_perks_for_level(level: int, current_votes: int) -> Dict[str, Any]:
    """
    Derives unlocked perks and feature gates for Telegram/KuoteX channel level.
    """
    _, next_level_votes = compute_channel_level_from_votes(current_votes)
    
    return {
        "level": level,
        "current_votes": current_votes,
        "next_level_required_votes": next_level_votes,
        "custom_color_unlocked": level >= 1,
        "status_emoji_unlocked": level >= 2,
        "wallpaper_unlocked": level >= 3,
        "custom_reactions_unlocked": level >= 4,
        "custom_cover_unlocked": level >= 7,
        "emoji_pack_unlocked": level >= 8,
        "stories_per_day_limit": max(0, level * 2)
    }


async def recalculate_channel_level(
    db: firestore.AsyncClient,
    channel_id: str,
    transaction: Optional[Transaction] = None
) -> Dict[str, Any]:
    """
    Recalculates level, next-level requirements, and updates feature flags
    for the specified channel document.
    Can run standalone or inside an existing atomic transaction.
    """
    channel_ref = db.collection("channels").document(channel_id)
    now_ms = int(time.time() * 1000)

    if transaction is not None:
        channel_snap = await channel_ref.get(transaction=transaction)
        if not channel_snap.exists:
            raise ChannelNotFoundError(channel_id)
        
        channel_data = channel_snap.to_dict() or {}
        current_votes = int(channel_data.get("current_votes", 0))
        level, next_req = compute_channel_level_from_votes(current_votes)
        perks = calculate_perks_for_level(level, current_votes)
        
        update_payload = {
            **perks,
            "updated_at": now_ms
        }
        transaction.update(channel_ref, update_payload)
        return update_payload

    # Non-transactional fallback
    channel_snap = await channel_ref.get()
    if not channel_snap.exists:
        raise ChannelNotFoundError(channel_id)

    channel_data = channel_snap.to_dict() or {}
    current_votes = int(channel_data.get("current_votes", 0))
    level, next_req = compute_channel_level_from_votes(current_votes)
    perks = calculate_perks_for_level(level, current_votes)

    update_payload = {
        **perks,
        "updated_at": now_ms
    }
    await channel_ref.update(update_payload)
    return update_payload


async def apply_channel_boost(
    db: firestore.AsyncClient,
    user_id: str,
    channel_id: str,
    votes_count: int = 1
) -> Dict[str, Any]:
    """
    Validates user privileges, decrements available vote balance,
    increments channel vote tally, and recalculates channel level progression atomically.
    
    Business Rules:
    1. Only allows the boost if:
       - user.vip_status == True (and not expired), OR
       - user.role in ['admin', 'developer']
    2. Ensures user has sufficient available_boost_votes.
    3. Atomically updates:
       - user doc (allocated_boosts + available_boost_votes)
       - channel doc (current_votes + level progression)
       - ledger_transactions doc (immutable audit trace)
    """
    if votes_count <= 0:
        raise ValueError("votes_count must be greater than 0")

    now_ms = int(time.time() * 1000)
    tx_id = f"tx_boost_{now_ms}_{uuid.uuid4().hex[:8]}"

    user_ref = db.collection("users").document(user_id)
    channel_ref = db.collection("channels").document(channel_id)
    ledger_ref = db.collection("ledger_transactions").document(tx_id)

    @firestore.async_transactional
    async def _tx_boost_step(transaction: Transaction) -> Dict[str, Any]:
        # 1. Fetch User & Channel Document snapshots
        user_snap = await user_ref.get(transaction=transaction)
        channel_snap = await channel_ref.get(transaction=transaction)

        if not user_snap.exists:
            raise UserNotFoundError(user_id)
        if not channel_snap.exists:
            raise ChannelNotFoundError(channel_id)

        user_data = user_snap.to_dict() or {}
        channel_data = channel_snap.to_dict() or {}

        # 2. Privilege Validation
        user_role = str(user_data.get("role", "user")).lower()
        vip_status = bool(user_data.get("vip_status", False))
        vip_expiration = int(user_data.get("vip_expiration", 0))

        is_vip_active = vip_status and (vip_expiration == 0 or now_ms < vip_expiration)
        has_role_privilege = user_role in ["admin", "developer"]

        if not (is_vip_active or has_role_privilege):
            raise UnauthorizedBoostError(
                f"User '{user_id}' does not have VIP status or Admin/Developer role."
            )

        # 3. Available Votes Limit Check
        is_dev = user_role == "developer"
        available_votes = int(user_data.get("available_boost_votes", 0))

        if not is_dev and available_votes < votes_count:
            raise InsufficientBoostVotesError(available=available_votes, requested=votes_count)

        remaining_votes = available_votes if is_dev else (available_votes - votes_count)

        # 4. User Allocated Boosts Update
        allocated_boosts = list(user_data.get("allocated_boosts", []))
        found_existing = False
        for entry in allocated_boosts:
            if entry.get("channel_id") == channel_id:
                entry["votes_count"] = int(entry.get("votes_count", 0)) + votes_count
                entry["boosted_at"] = now_ms
                found_existing = True
                break

        if not found_existing:
            allocated_boosts.append({
                "channel_id": channel_id,
                "votes_count": votes_count,
                "boosted_at": now_ms
            })

        transaction.update(user_ref, {
            "available_boost_votes": remaining_votes,
            "allocated_boosts": allocated_boosts,
            "updated_at": now_ms
        })

        # 5. Channel Vote Count & Level Recalculation
        current_votes = int(channel_data.get("current_votes", 0))
        new_total_votes = current_votes + votes_count
        new_level, next_req = compute_channel_level_from_votes(new_total_votes)
        perks_payload = calculate_perks_for_level(new_level, new_total_votes)

        transaction.update(channel_ref, {
            **perks_payload,
            "updated_at": now_ms
        })

        # 6. Immutable Ledger Record
        ledger_doc = {
            "tx_id": tx_id,
            "idempotency_key": f"boost_{user_id}_{channel_id}_{now_ms}",
            "type": "CHANNEL_BOOST",
            "from_user_id": user_id,
            "to_user_id": channel_id,
            "amount": votes_count,
            "fee": 0,
            "status": "COMMITTED",
            "metadata": {
                "votes_applied": votes_count,
                "new_channel_level": new_level,
                "total_channel_votes": new_total_votes
            },
            "created_at": now_ms
        }
        transaction.set(ledger_ref, ledger_doc)

        return {
            "_": "channelBoostSuccess",
            "tx_id": tx_id,
            "channel_id": channel_id,
            "user_id": user_id,
            "applied_votes": votes_count,
            "remaining_votes": remaining_votes,
            "channel_perks": perks_payload,
            "boosted_at": now_ms
        }

    # Execute atomic Firestore transaction
    tx = db.transaction()
    return await _tx_boost_step(tx)
