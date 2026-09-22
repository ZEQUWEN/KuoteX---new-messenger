import firebase_admin
from firebase_admin import credentials, messaging
from fastapi import FastAPI, Request, HTTPException
import uvicorn
import os

app = FastAPI()

# Initialize Firebase Admin SDK
# In a real scenario, GOOGLE_APPLICATION_CREDENTIALS environment variable should be set,
# or a dict should be passed to Certificate()
try:
    firebase_admin.initialize_app()
except ValueError:
    pass # Already initialized

@app.post("/webhook/profile-update")
async def profile_update_webhook(request: Request):
    """
    Webhook endpoint to be called by Supabase (or any trigger) when a profile is updated.
    Expects a JSON payload containing the user's updated data and FCM token.
    """
    try:
        data = await request.json()
        
        # Extract data from Supabase webhook payload
        # Assuming payload structure from Supabase Database Webhook
        record = data.get("record", {})
        fcm_token = record.get("fcm_token")
        
        if not fcm_token:
            return {"status": "ignored", "reason": "No FCM token available for user"}
            
        # Determine what changed (could be phone, email, or general profile data)
        old_record = data.get("old_record", {})
        
        changes = []
        if record.get("phone") != old_record.get("phone"):
            changes.append("номер телефона")
        if record.get("email") != old_record.get("email"):
            changes.append("email")
        if record.get("first_name") != old_record.get("first_name") or record.get("last_name") != old_record.get("last_name"):
            changes.append("имя")
        
        if not changes:
            changes.append("данные профиля")
            
        change_text = ", ".join(changes)
            
        # Create and send the FCM message
        message = messaging.Message(
            notification=messaging.Notification(
                title="Безопасность аккаунта",
                body=f"Ваш {change_text} был успешно изменен."
            ),
            token=fcm_token,
        )
        
        response = messaging.send(message)
        return {"status": "success", "message_id": response}
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=int(os.environ.get("PORT", 8080)))
