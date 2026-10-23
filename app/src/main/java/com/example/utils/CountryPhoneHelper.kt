package com.example.utils

import android.content.ContentProviderOperation
import android.content.Context
import android.provider.ContactsContract

data class CountryInfo(
    val id: String,
    val nameRu: String,
    val nameEn: String,
    val dialCode: String,
    val flagEmoji: String,
    val mask: String = "000 000 0000",
    val maxDigits: Int = 10
)

data class DeviceContact(
    val name: String,
    val phoneNumber: String
)

object CountryPhoneHelper {

    val defaultCountry = CountryInfo(
        id = "RU",
        nameRu = "Россия",
        nameEn = "Russia",
        dialCode = "+7",
        flagEmoji = "🇷🇺",
        mask = "000 000 0000",
        maxDigits = 10
    )

    val countries: List<CountryInfo> = listOf(
        defaultCountry,
        CountryInfo("KZ", "Казахстан", "Kazakhstan", "+7", "🇰🇿", "000 000 0000", 10),
        CountryInfo("BY", "Беларусь", "Belarus", "+375", "🇧🇾", "00 000 0000", 9),
        CountryInfo("UA", "Украина", "Ukraine", "+380", "🇺🇦", "00 000 0000", 9),
        CountryInfo("UZ", "Узбекистан", "Uzbekistan", "+998", "🇺🇿", "00 000 0000", 9),
        CountryInfo("AM", "Армения", "Armenia", "+374", "🇦🇲", "00 000 000", 8),
        CountryInfo("GE", "Грузия", "Georgia", "+995", "🇬🇪", "000 000 000", 9),
        CountryInfo("AZ", "Азербайджан", "Azerbaijan", "+994", "🇦🇿", "00 000 0000", 9),
        CountryInfo("KG", "Кыргызстан", "Kyrgyzstan", "+996", "🇰🇬", "000 000 000", 9),
        CountryInfo("TJ", "Таджикистан", "Tajikistan", "+992", "🇹🇯", "00 000 0000", 9),
        CountryInfo("TM", "Туркменистан", "Turkmenistan", "+993", "🇹🇲", "0 000 0000", 8),
        CountryInfo("MD", "Молдова", "Moldova", "+373", "🇲🇩", "000 00000", 8),
        CountryInfo("US", "США", "United States", "+1", "🇺🇸", "000 000 0000", 10),
        CountryInfo("CA", "Канада", "Canada", "+1", "🇨🇦", "000 000 0000", 10),
        CountryInfo("DE", "Германия", "Germany", "+49", "🇩🇪", "000 0000000", 10),
        CountryInfo("GB", "Великобритания", "United Kingdom", "+44", "🇬🇧", "0000 000000", 10),
        CountryInfo("FR", "Франция", "France", "+33", "🇫🇷", "0 00 00 00 00", 9),
        CountryInfo("IT", "Италия", "Italy", "+39", "🇮🇹", "000 000 0000", 10),
        CountryInfo("ES", "Испания", "Spain", "+34", "🇪🇸", "000 000 000", 9),
        CountryInfo("TR", "Турция", "Turkey", "+90", "🇹🇷", "000 000 0000", 10),
        CountryInfo("AE", "ОАЭ", "United Arab Emirates", "+971", "🇦🇪", "0 000 0000", 9),
        CountryInfo("IL", "Израиль", "Israel", "+972", "🇮🇱", "00 000 0000", 9),
        CountryInfo("PL", "Польша", "Poland", "+48", "🇵🇱", "000 000 000", 9),
        CountryInfo("CN", "Китай", "China", "+86", "🇨🇳", "000 0000 0000", 11),
        CountryInfo("IN", "Индия", "India", "+91", "🇮🇳", "00000 00000", 10),
        CountryInfo("JP", "Япония", "Japan", "+81", "🇯🇵", "00 0000 0000", 10),
        CountryInfo("KR", "Южная Корея", "South Korea", "+82", "🇰🇷", "00 0000 0000", 10),
        CountryInfo("RS", "Сербия", "Serbia", "+381", "🇷🇸", "00 000 0000", 9),
        CountryInfo("CY", "Кипр", "Cyprus", "+357", "🇨🇾", "00 000000", 8),
        CountryInfo("GR", "Греция", "Greece", "+30", "🇬🇷", "000 000 0000", 10),
        CountryInfo("CZ", "Чехия", "Czech Republic", "+420", "🇨🇿", "000 000 000", 9),
        CountryInfo("AT", "Австрия", "Austria", "+43", "🇦🇹", "000 000000", 9),
        CountryInfo("CH", "Швейцария", "Switzerland", "+41", "🇨🇭", "00 000 0000", 9),
        CountryInfo("NL", "Нидерланды", "Netherlands", "+31", "🇳🇱", "0 0000 0000", 9),
        CountryInfo("SE", "Швеция", "Sweden", "+46", "🇸🇪", "00 000 0000", 9),
        CountryInfo("NO", "Норвегия", "Norway", "+47", "🇳🇴", "000 00 000", 8),
        CountryInfo("FI", "Финляндия", "Finland", "+358", "🇫🇮", "00 000 0000", 9),
        CountryInfo("EE", "Эстония", "Estonia", "+372", "🇪🇪", "0000 0000", 8),
        CountryInfo("LV", "Латвия", "Latvia", "+371", "🇱🇻", "00 000 000", 8),
        CountryInfo("LT", "Литва", "Lithuania", "+370", "🇱🇹", "000 00000", 8),
        CountryInfo("BR", "Бразилия", "Brazil", "+55", "🇧🇷", "00 00000 0000", 11),
        CountryInfo("AR", "Аргентина", "Argentina", "+54", "🇦🇷", "0 00 0000 0000", 11),
        CountryInfo("EG", "Египет", "Egypt", "+20", "🇪🇬", "00 0000 0000", 10),
        CountryInfo("TH", "Таиланд", "Thailand", "+66", "🇹🇭", "00 000 0000", 9),
        CountryInfo("VN", "Вьетнам", "Vietnam", "+84", "🇻🇳", "00 0000 0000", 9),
        CountryInfo("ID", "Индонезия", "Indonesia", "+62", "🇮🇩", "000 0000 0000", 11)
    )

    private val sortedByDialCodeDesc = countries.sortedByDescending { it.dialCode.length }

    /**
     * Auto-detect country info and remaining national digits from a raw string.
     */
    fun detectCountry(rawInput: String): Pair<CountryInfo, String> {
        val clean = rawInput.trim()
        val digitsOnly = clean.filter { it.isDigit() }

        if (clean.startsWith("+")) {
            for (country in sortedByDialCodeDesc) {
                val codeDigits = country.dialCode.removePrefix("+")
                if (digitsOnly.startsWith(codeDigits)) {
                    val remaining = digitsOnly.removePrefix(codeDigits)
                    return Pair(country, remaining)
                }
            }
        } else if (clean.startsWith("89") || clean.startsWith("79")) {
            // Russian local dialing convention
            return Pair(defaultCountry, digitsOnly.drop(1))
        } else if (digitsOnly.length >= 2) {
            for (country in sortedByDialCodeDesc) {
                val codeDigits = country.dialCode.removePrefix("+")
                if (digitsOnly.startsWith(codeDigits) && codeDigits.length >= 2) {
                    val remaining = digitsOnly.removePrefix(codeDigits)
                    return Pair(country, remaining)
                }
            }
        }

        return Pair(defaultCountry, digitsOnly)
    }

    /**
     * Format national phone digits according to a country mask (e.g. 000 000 0000)
     */
    fun formatNationalNumber(digits: String, mask: String): String {
        val cleanDigits = digits.filter { it.isDigit() }
        val sb = StringBuilder()
        var digitIndex = 0

        for (ch in mask) {
            if (digitIndex >= cleanDigits.length) break
            if (ch == '0') {
                sb.append(cleanDigits[digitIndex])
                digitIndex++
            } else {
                sb.append(ch)
            }
        }

        // If extra digits beyond the mask, append separated by space
        if (digitIndex < cleanDigits.length) {
            sb.append(" ").append(cleanDigits.substring(digitIndex))
        }

        return sb.toString().trim()
    }

    /**
     * Fetch contacts from the Android device address book.
     */
    fun fetchDeviceContacts(context: Context): List<DeviceContact> {
        val result = mutableListOf<DeviceContact>()
        try {
            val contentResolver = context.contentResolver
            val cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )

            cursor?.use {
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (it.moveToNext()) {
                    val name = if (nameIndex >= 0) it.getString(nameIndex) else null
                    val number = if (numberIndex >= 0) it.getString(numberIndex) else null

                    if (!name.isNullOrBlank() && !number.isNullOrBlank()) {
                        result.add(DeviceContact(name.trim(), number.trim()))
                    }
                }
            }
        } catch (_: Exception) {
            // Permission not granted or query failed
        }
        return result.distinctBy { it.phoneNumber.filter { ch -> ch.isDigit() } }
    }

    /**
     * Saves a new contact into the device's native address book.
     */
    fun saveContactToDevice(
        context: Context,
        firstName: String,
        lastName: String,
        phoneNumber: String,
        notes: String
    ): Boolean {
        return try {
            val ops = ArrayList<ContentProviderOperation>()

            val rawContactInsertIndex = ops.size
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build()
            )

            // Name
            val fullName = listOf(firstName.trim(), lastName.trim()).filter { it.isNotBlank() }.joinToString(" ")
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, firstName.trim())
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, lastName.trim())
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, fullName)
                    .build()
            )

            // Phone
            if (phoneNumber.isNotBlank()) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phoneNumber)
                        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                        .build()
                )
            }

            // Notes
            if (notes.isNotBlank()) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Note.NOTE, notes)
                        .build()
                )
            }

            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        } catch (_: Exception) {
            false
        }
    }
}
