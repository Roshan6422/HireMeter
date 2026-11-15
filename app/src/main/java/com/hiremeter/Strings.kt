package com.hiremeter

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class Lang(val label: String) { SI("සිංහල"), TA("தமிழ்"), EN("English") }

/** Every piece of text the app shows, in one language. `%s` / `%d` are filled in with String.format. */
class Strings(
    val currency: String,
    val tabMeter: String, val tabRates: String, val tabTrips: String, val tabSettings: String,
    val date: String, val distanceFare: String, val timeFare: String,
    val fare: String, val distance: String, val elapsed: String,
    val startTrip: String, val endTrip: String, val gpsSearching: String,
    /** first km, later km, per minute */
    val rateLine: String,
    /** distance fare, time fare */
    val fareSplit: String,
    val meterNote: String,
    val gpsOff: String, val noPermission: String,
    val billTitle: String, val total: String, val vehicleLabel: String, val ok: String, val sendBill: String,
    val payByQr: String, val payTitle: String, val amountDue: String, val payHint: String,
    val ratesTitle: String, val ratesLocked: String, val ratesHint: String,
    val firstKm: String, val nextKm: String, val perMinLabel: String,
    val adjustAllTitle: String, val adjustAllHint: String,
    /** minimum fare */
    val minFareLine: String,
    val minFareLabel: String, val minFareTopUp: String,
    val payCardTitle: String, val payCardHint: String, val pickQr: String, val changeQr: String,
    val remove: String, val accountLabel: String, val badImage: String,
    val tripsTitle: String,
    /** number of trips today */
    val todayIncome: String,
    val noTrips: String, val clearTrips: String, val clearConfirmTitle: String, val clearConfirmText: String,
    val delete: String, val cancel: String,
    val notifText: String, val language: String,
    val pauseTrip: String, val resumeTrip: String,
    val endConfirmTitle: String, val tripRecovered: String,
    val batteryTitle: String, val batteryHint: String, val batteryButton: String, val batteryDone: String,
    /** time paused so far */
    val pausedBanner: String,
    val pausedTime: String, val notifPaused: String,
    val pausedCharged: String, val waitingFareLabel: String, val waitRateLabel: String,
    /** waiting time as the customer's bill names it */
    val waitedLabel: String,
    /** waiting rate per minute */
    val chargePausedLabel: String,
    val viewOnGoogle: String, val routeLink: String,
    val tuk: String, val bike: String, val mini: String, val flex: String, val car: String, val minivan: String, val van: String,
) {
    fun vehicleName(id: String) = when (id) {
        "tuk" -> tuk
        "bike" -> bike
        "mini" -> mini
        "flex" -> flex
        "car" -> car
        "minivan" -> minivan
        else -> van
    }
}

private val SINHALA = Strings(
    currency = "රු.",
    tabMeter = "මීටරය", tabRates = "මිල ගණන්", tabTrips = "ගමන්", tabSettings = "සැකසුම්",
    date = "දිනය", distanceFare = "දුරට ගාස්තුව", timeFare = "කාලයට ගාස්තුව",
    fare = "ගාස්තුව", distance = "දුර", elapsed = "ගත වූ කාලය",
    startTrip = "ගමන පටන් ගන්න", endTrip = "ගමන ඉවර කරන්න",
    gpsSearching = "📡 GPS signal එක හොයනවා… එළිමහනට යන්න",
    rateLine = "පළමු km %s · ඊළඟ km %s · විනාඩියට %s",
    fareSplit = "දුරට %s + කාලයට %s",
    meterNote = "ගාස්තුව = දුරට (km එකකට) + මුළු ගමන් කාලයටම (විනාඩියකට). PickMe / Uber වගේමයි.",
    gpsOff = "Location (GPS) එක on කරන්න", noPermission = "Location permission එක නැතුව දුර මනින්න බෑ",
    billTitle = "ගමන් බිල", total = "මුළු ගාස්තුව", vehicleLabel = "වාහනය", ok = "හරි", sendBill = "බිල යවන්න",
    payByQr = "📱 QR එකෙන් ගෙවන්න", payTitle = "QR එක scan කරලා ගෙවන්න", amountDue = "ගෙවිය යුතු මුදල",
    payHint = "Bank app එකෙන් scan කරලා, උඩ තියෙන මුදල type කරන්න.",
    ratesTitle = "මිල ගණන් (රුපියල්)", ratesLocked = "ගමනක් යන අතරතුර මිල වෙනස් කරන්න බෑ.",
    ratesHint = "ඔබේ මිල ගණන් මෙතන දාන්න. ලියන ගමන් save වෙනවා.",
    firstKm = "පළමු km", nextKm = "ඊළඟ km එකකට", perMinLabel = "විනාඩියකට",
    adjustAllTitle = "සියලු මිල එක පාරින් වෙනස් කරන්න",
    minFareLine = "අවම ගාස්තුව %s",
    minFareLabel = "අවම ගාස්තුව (0 = නැත)", minFareTopUp = "අවම ගාස්තුවට එකතු කළ මුදල",
    adjustAllHint = "ප්‍රතිශතය (උදා. 2 හෝ 3) දාලා − හෝ + ඔබන්න. හැම වාහනයකම මිල ගණන් ඒ ප්‍රමාණයෙන් අඩු / වැඩි වෙනවා.",
    payCardTitle = "📱 QR ගෙවීම් (ඔබේ ගිණුම)",
    payCardHint = "ඔබේ බැංකුවෙන් / payment app එකෙන් දුන්න QR එකේ පින්තූරය මෙතනට දාන්න. බිල් එකේදී customer ට scan කරන්න පෙන්නනවා.",
    pickQr = "QR පින්තූරය තෝරන්න", changeQr = "QR එක වෙනස් කරන්න", remove = "අයින් කරන්න",
    accountLabel = "ගිණුම් විස්තර (නම, බැංකුව, ගිණුම් අංකය)", badImage = "ඒ පින්තූරය කියවන්න බැරි වුණා",
    tripsTitle = "ගමන්", todayIncome = "අද ආදායම · ගමන් %d", noTrips = "තාම ගමන් නෑ.",
    clearTrips = "ගමන් ලැයිස්තුව මකන්න", clearConfirmTitle = "ගමන් ඔක්කොම මකන්නද?",
    clearConfirmText = "මේක ආයේ ගන්න බෑ.", delete = "මකන්න", cancel = "එපා",
    notifText = "ගමන මනිමින් පවතී", language = "භාෂාව",
    pauseTrip = "මීටරය නවත්වන්න (Pause)", resumeTrip = "මීටරය ආයෙ පටන් ගන්න",
    endConfirmTitle = "ගමන ඉවර කරන්නද?",
    tripRecovered = "⚠️ නැවතිලා තිබුණු ගමන ආයෙ ලබාගත්තා. App එක නැවතිලා තිබුණු වෙලාවේ දුර අඩුවෙන් ගණන් වෙන්න පුළුවන්.",
    batteryTitle = "🔋 Battery",
    batteryHint = "Battery saver එකෙන් ගමනක් යනකොට app එක නවත්වන්න පුළුවන්. මේක ඔබලා HireMeter එකට battery limit අයින් කරන්න. Samsung phone වල Settings → Battery → Background usage limits එකේ \"Sleeping apps\" වලිනුත් HireMeter අයින් කරන්න.",
    batteryButton = "Battery limit අයින් කරන්න", batteryDone = "✓ HireMeter එකට battery limit නෑ",
    pausedBanner = "⏸ මීටරය නවත්වලා · %s\nදුර සහ කාලය ගණන් වෙන්නේ නෑ. GPS එක off.",
    pausedTime = "නවත්වා තිබූ කාලය (ගාස්තු නැත)", notifPaused = "මීටරය නවත්වා ඇත (Pause)",
    pausedCharged = "රැඳී සිටි කාලය (Pause)", waitingFareLabel = "රැඳී සිටීමට ගාස්තුව", waitRateLabel = "රැඳීම / විනාඩි (Pause)", waitedLabel = "රැඳී සිටි කාලය",
    chargePausedLabel = "Pause කාලයට රැඳී සිටීමේ ගාස්තුව ගන්න · විනාඩියට %s",
    viewOnGoogle = "🗺️ Google Maps එකෙන් බලන්න", routeLink = "ගිය පාර:",
    tuk = "ත්‍රීවීල්", bike = "බයික්", mini = "මිනි කාර්", flex = "ෆ්ලෙක්ස්", car = "කාර්", minivan = "මිනි වෑන්", van = "වෑන්",
)

private val TAMIL = Strings(
    currency = "ரூ.",
    tabMeter = "மீட்டர்", tabRates = "கட்டணங்கள்", tabTrips = "பயணங்கள்", tabSettings = "அமைப்புகள்",
    date = "தேதி", distanceFare = "தூரக் கட்டணம்", timeFare = "நேரக் கட்டணம்",
    fare = "கட்டணம்", distance = "தூரம்", elapsed = "மொத்த நேரம்",
    startTrip = "பயணத்தைத் தொடங்கு", endTrip = "பயணத்தை முடி",
    gpsSearching = "📡 GPS சிக்னலைத் தேடுகிறது… திறந்த வெளிக்குச் செல்லவும்",
    rateLine = "முதல் km %s · அடுத்த km %s · நிமிடத்திற்கு %s",
    fareSplit = "தூரத்திற்கு %s + நேரத்திற்கு %s",
    meterNote = "கட்டணம் = தூரம் (km-க்கு) + மொத்த பயண நேரம் (நிமிடத்திற்கு). PickMe / Uber போலவே.",
    gpsOff = "Location (GPS)-ஐ இயக்கவும்", noPermission = "Location அனுமதி இல்லாமல் தூரத்தை அளக்க முடியாது",
    billTitle = "பயண பில்", total = "மொத்தக் கட்டணம்", vehicleLabel = "வாகனம்", ok = "சரி", sendBill = "பில்லை அனுப்பு",
    payByQr = "📱 QR மூலம் செலுத்து", payTitle = "QR-ஐ ஸ்கேன் செய்து செலுத்தவும்", amountDue = "செலுத்த வேண்டிய தொகை",
    payHint = "வங்கி app மூலம் ஸ்கேன் செய்து, மேலே உள்ள தொகையை உள்ளிடவும்.",
    ratesTitle = "கட்டணங்கள் (ரூபாய்)", ratesLocked = "பயணத்தின் போது கட்டணங்களை மாற்ற முடியாது.",
    ratesHint = "உங்கள் கட்டணங்களை இங்கே உள்ளிடவும். தட்டச்சு செய்யும்போதே சேமிக்கப்படும்.",
    firstKm = "முதல் km", nextKm = "அடுத்த km-க்கு", perMinLabel = "நிமிடத்திற்கு",
    adjustAllTitle = "எல்லாக் கட்டணங்களையும் ஒரே முறையில் மாற்று",
    minFareLine = "குறைந்தபட்சக் கட்டணம் %s",
    minFareLabel = "குறைந்தபட்சக் கட்டணம் (0 = இல்லை)", minFareTopUp = "குறைந்தபட்சக் கட்டணத்திற்காகச் சேர்த்தது",
    adjustAllHint = "சதவீதத்தை (எ.கா. 2 அல்லது 3) உள்ளிட்டு − அல்லது + அழுத்தவும். எல்லா வாகனங்களின் கட்டணங்களும் அந்த அளவு குறையும் / கூடும்.",
    payCardTitle = "📱 QR கட்டணம் (உங்கள் கணக்கு)",
    payCardHint = "உங்கள் வங்கி / payment app வழங்கிய QR படத்தை இங்கே சேர்க்கவும். பில்லில் வாடிக்கையாளர் ஸ்கேன் செய்யக் காட்டப்படும்.",
    pickQr = "QR படத்தைத் தேர்ந்தெடு", changeQr = "QR-ஐ மாற்று", remove = "நீக்கு",
    accountLabel = "கணக்கு விவரங்கள் (பெயர், வங்கி, கணக்கு எண்)", badImage = "அந்தப் படத்தைப் படிக்க முடியவில்லை",
    tripsTitle = "பயணங்கள்", todayIncome = "இன்றைய வருமானம் · பயணங்கள் %d", noTrips = "இன்னும் பயணங்கள் இல்லை.",
    clearTrips = "பயணப் பட்டியலை அழி", clearConfirmTitle = "எல்லாப் பயணங்களையும் அழிக்கவா?",
    clearConfirmText = "இதை மீட்டெடுக்க முடியாது.", delete = "அழி", cancel = "வேண்டாம்",
    notifText = "பயணம் அளக்கப்படுகிறது", language = "மொழி",
    pauseTrip = "மீட்டரை இடைநிறுத்து", resumeTrip = "மீட்டரைத் தொடர்",
    endConfirmTitle = "பயணத்தை முடிக்கவா?",
    tripRecovered = "⚠️ நின்றுபோன பயணம் மீட்டெடுக்கப்பட்டது. App நின்றிருந்த நேரத்தின் தூரம் குறைவாகக் கணக்கிடப்படலாம்.",
    batteryTitle = "🔋 Battery",
    batteryHint = "Battery saver பயணத்தின் போது app-ஐ நிறுத்தக்கூடும். இதை அழுத்தி HireMeter-க்கு battery வரம்பை நீக்கவும். Samsung போன்களில் Settings → Battery → Background usage limits-இல் \"Sleeping apps\" பட்டியலிலிருந்தும் HireMeter-ஐ நீக்கவும்.",
    batteryButton = "Battery வரம்பை நீக்கு", batteryDone = "✓ HireMeter-க்கு battery வரம்பு இல்லை",
    pausedBanner = "⏸ மீட்டர் இடைநிறுத்தப்பட்டுள்ளது · %s\nதூரமும் நேரமும் கணக்கிடப்படாது. GPS அணைக்கப்பட்டுள்ளது.",
    pausedTime = "இடைநிறுத்திய நேரம் (கட்டணம் இல்லை)", notifPaused = "மீட்டர் இடைநிறுத்தப்பட்டுள்ளது",
    pausedCharged = "காத்திருந்த நேரம் (இடைநிறுத்தம்)", waitingFareLabel = "காத்திருப்புக் கட்டணம்", waitRateLabel = "காத்திருப்பு / நிமிடம்", waitedLabel = "காத்திருந்த நேரம்",
    chargePausedLabel = "இடைநிறுத்த நேரத்திற்குக் காத்திருப்புக் கட்டணம் · நிமிடத்திற்கு %s",
    viewOnGoogle = "🗺️ Google Maps-இல் பார்க்க", routeLink = "சென்ற பாதை:",
    tuk = "முச்சக்கர வண்டி", bike = "பைக்", mini = "மினி கார்", flex = "ஃப்ளெக்ஸ்", car = "கார்", minivan = "மினி வேன்", van = "வேன்",
)

private val ENGLISH = Strings(
    currency = "Rs.",
    tabMeter = "Meter", tabRates = "Rates", tabTrips = "Trips", tabSettings = "Settings",
    date = "Date", distanceFare = "Distance fare", timeFare = "Time fare",
    fare = "Fare", distance = "Distance", elapsed = "Trip time",
    startTrip = "Start trip", endTrip = "End trip",
    gpsSearching = "📡 Looking for GPS signal… move into the open",
    rateLine = "First km %s · next km %s · %s per minute",
    fareSplit = "Distance %s + time %s",
    meterNote = "Fare = distance (per km) + the whole trip time (per minute), the same as PickMe and Uber.",
    gpsOff = "Turn on Location (GPS)", noPermission = "Distance cannot be measured without the Location permission",
    billTitle = "Trip bill", total = "Total fare", vehicleLabel = "Vehicle", ok = "OK", sendBill = "Send bill",
    payByQr = "📱 Pay by QR", payTitle = "Scan the QR to pay", amountDue = "Amount to pay",
    payHint = "Scan with your bank app and enter the amount shown above.",
    ratesTitle = "Rates (rupees)", ratesLocked = "Rates cannot be changed during a trip.",
    ratesHint = "Enter your own rates here. They are saved as you type.",
    firstKm = "First km", nextKm = "Each next km", perMinLabel = "Per minute",
    adjustAllTitle = "Change all rates at once",
    minFareLine = "Minimum fare %s",
    minFareLabel = "Minimum fare (0 = none)", minFareTopUp = "Added to reach minimum fare",
    adjustAllHint = "Type a percentage (e.g. 2 or 3), then press − or +. Every vehicle's rates go down / up by that much.",
    payCardTitle = "📱 QR payments (your account)",
    payCardHint = "Add the picture of the QR your bank or payment app gave you. It is shown on the bill for the customer to scan.",
    pickQr = "Choose QR picture", changeQr = "Change QR", remove = "Remove",
    accountLabel = "Account details (name, bank, account number)", badImage = "That picture could not be read",
    tripsTitle = "Trips", todayIncome = "Today's income · %d trips", noTrips = "No trips yet.",
    clearTrips = "Clear trip list", clearConfirmTitle = "Delete all trips?",
    clearConfirmText = "This cannot be undone.", delete = "Delete", cancel = "Cancel",
    notifText = "Measuring the trip", language = "Language",
    pauseTrip = "Pause meter", resumeTrip = "Resume meter",
    endConfirmTitle = "End the trip?",
    tripRecovered = "⚠️ The stopped trip was recovered. Distance while the app was stopped may be counted short.",
    batteryTitle = "🔋 Battery",
    batteryHint = "Battery saving can stop the app during a trip. Tap below to take HireMeter off battery limits. On Samsung phones, also remove HireMeter from \"Sleeping apps\" (Settings → Battery → Background usage limits).",
    batteryButton = "Remove battery limit", batteryDone = "✓ HireMeter has no battery limit",
    pausedBanner = "⏸ Meter paused · %s\nDistance and time are not counted. GPS is off.",
    pausedTime = "Paused (not charged)", notifPaused = "Meter paused",
    pausedCharged = "Waited (paused)", waitingFareLabel = "Waiting fare", waitRateLabel = "Waiting / min (paused)", waitedLabel = "Waiting time",
    chargePausedLabel = "Charge the paused time as waiting · %s per minute",
    viewOnGoogle = "🗺️ View on Google Maps", routeLink = "Route:",
    tuk = "Three-wheeler", bike = "Bike", mini = "Mini car", flex = "Flex", car = "Car", minivan = "Mini van", van = "Van",
)

/** The language in use. Screens that read [s] redraw when [lang] changes. */
object Tr {
    var lang by mutableStateOf(Lang.SI)
        private set

    val s: Strings
        get() = when (lang) {
            Lang.SI -> SINHALA
            Lang.TA -> TAMIL
            Lang.EN -> ENGLISH
        }

    fun load(ctx: Context) {
        val saved = ctx.getSharedPreferences("lang", Context.MODE_PRIVATE).getString("lang", null)
        lang = Lang.values().firstOrNull { it.name == saved } ?: Lang.SI
    }

    fun set(ctx: Context, l: Lang) {
        lang = l
        ctx.getSharedPreferences("lang", Context.MODE_PRIVATE).edit().putString("lang", l.name).apply()
    }
}
