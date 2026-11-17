package com.hiremeter

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class Lang(val label: String) { SI("සිංහල"), TA("தமிழ்"), EN("English") }

/** Every piece of text the app shows, in one language. `%s` / `%d` are filled in with String.format. */
class Strings(
    val currency: String,
    val tabMeter: String, val tabSpeed: String, val tabRates: String, val tabTrips: String, val tabSettings: String,
    val speedTitle: String, val topSpeed: String, val resetTopSpeed: String, val speedNote: String, val allowLocation: String,
    /** [timeFare] is only shown for trips from version 1.3, which charged trip time per minute */
    val date: String, val distanceFare: String, val timeFare: String,
    val fare: String, val distance: String, val elapsed: String,
    val startTrip: String, val endTrip: String, val gpsSearching: String,
    /** first km, later km, waiting per minute */
    val rateLine: String,
    /** distance fare, waiting fare */
    val fareSplit: String,
    val meterNote: String,
    val gpsOff: String, val noPermission: String,
    val billTitle: String, val total: String, val vehicleLabel: String, val ok: String, val sendBill: String,
    val payByQr: String, val payTitle: String, val amountDue: String, val payHint: String,
    val ratesTitle: String, val ratesLocked: String, val ratesHint: String,
    val firstKm: String, val nextKm: String,
    val freeStopTitle: String, val freeStopHint: String, val freeStopLabel: String,
    /** how long stopped now; minutes after which a stop is waiting */
    val stoppedNote: String,
    /** the same, at a traffic light */
    val lightNote: String,
    /** how long stopped now */
    val lightMarkedNote: String,
    val markLight: String, val signalFreeLabel: String,
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
    /** days trips are kept */
    val tripsKeptNote: String,
    val deleteTrip: String, val deleteTripTitle: String,
    val delete: String, val cancel: String,
    val notifText: String, val language: String,
    val pauseTrip: String, val resumeTrip: String,
    /** asked before pausing, so a stray tap while driving does not stop the meter */
    val pauseConfirmTitle: String, val pauseConfirmText: String, val pauseConfirmYes: String,
    /** the setting that shows the button for Uber / PickMe hires in between */
    val otherHiresTitle: String, val otherHiresHint: String,
    val endConfirmTitle: String, val tripRecovered: String,
    val batteryTitle: String, val batteryHint: String, val batteryButton: String, val batteryDone: String,
    /** Xiaomi / Redmi / POCO only */
    val autostartHint: String, val autostartButton: String,
    /** time paused so far */
    val pausedBanner: String,
    val pausedTime: String, val notifPaused: String,
    val pausedCharged: String, val waitingFareLabel: String, val waitRateLabel: String,
    /** waiting time as the customer's bill names it */
    val waitedLabel: String,
    /** waiting rate per minute */
    val chargePausedLabel: String,
    val viewOnGoogle: String, val routeLink: String,
    val backupTitle: String, val driveTurnOn: String, val driveHint: String,
    /** time of the last backup */
    val driveOnLine: String,
    /** the error */
    val driveFailed: String,
    val backupNow: String, val restoreFromDrive: String, val driveTurnOff: String,
    val fileBackup: String, val makeBackupFile: String, val restoreFile: String, val fileSaved: String,
    val restoreTitle: String,
    /** number of trips in the backup */
    val restoreText: String,
    val restoreYes: String,
    /** number of trips restored */
    val restoredMsg: String,
    val backingUp: String, val backedUp: String, val driveDone: String, val driveExisting: String, val driveNotLinked: String,
    val restoreLocked: String, val notABackup: String,
    /** the error */
    val backupFailed: String,
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
    tabMeter = "මීටරය", tabSpeed = "වේගය",
    speedTitle = "වාහනයේ වේගය", topSpeed = "වැඩිම වේගය", resetTopSpeed = "ආයෙ පටන් ගන්න",
    speedNote = "වේගය GPS එකෙන්. ඇත්ත වේගයට වඩා ටිකක් වෙනස් වෙන්න පුළුවන්. මීටරයේ ගාස්තුවට මේකෙන් බලපෑමක් නෑ.",
    allowLocation = "Location permission දෙන්න",
    tabRates = "මිල ගණන්", tabTrips = "ගමන්", tabSettings = "සැකසුම්",
    date = "දිනය", distanceFare = "දුරට ගාස්තුව", timeFare = "කාලයට ගාස්තුව",
    fare = "ගාස්තුව", distance = "දුර", elapsed = "ගත වූ කාලය",
    startTrip = "ගමන පටන් ගන්න", endTrip = "ගමන ඉවර කරන්න",
    gpsSearching = "📡 GPS signal එක හොයනවා… එළිමහනට යන්න",
    rateLine = "පළමු km %s · ඊළඟ km %s · රැඳීම විනාඩියට %s",
    fareSplit = "දුරට %s + රැඳීමට %s",
    meterNote = "ගාස්තුව = දුරට (km එකකට) + රැඳී සිටි කාලයට (විනාඩියකට). Signal එකක් වගේ කෙටි නැවතීම් වලට ගාස්තු නෑ.",
    gpsOff = "Location (GPS) එක on කරන්න", noPermission = "Location permission එක නැතුව දුර මනින්න බෑ",
    billTitle = "ගමන් බිල", total = "මුළු ගාස්තුව", vehicleLabel = "වාහනය", ok = "හරි", sendBill = "බිල යවන්න",
    payByQr = "📱 QR එකෙන් ගෙවන්න", payTitle = "QR එක scan කරලා ගෙවන්න", amountDue = "ගෙවිය යුතු මුදල",
    payHint = "Bank app එකෙන් scan කරලා, උඩ තියෙන මුදල type කරන්න.",
    ratesTitle = "මිල ගණන් (රුපියල්)", ratesLocked = "ගමනක් යන අතරතුර මිල වෙනස් කරන්න බෑ.",
    ratesHint = "ඔබේ මිල ගණන් මෙතන දාන්න. ලියන ගමන් save වෙනවා.",
    firstKm = "පළමු km", nextKm = "ඊළඟ km එකකට",
    freeStopTitle = "⏱️ රැඳී සිටීම ගණන් වෙන්නේ කවදාද?",
    freeStopHint = "වාහනය මේ විනාඩි ගානට වඩා නවත්වලා තිබුණොත්, ඒ නැවතීම මුල ඉඳලම රැඳී සිටීමක් විදියට ගණන් වෙනවා. ඊට අඩු නැවතීම් (signal, traffic) වලට ගාස්තු නෑ. හයර් එකේ මුල් මීටර් 700 යනකල් රැඳී සිටීම ගණන් වෙන්නේ නෑ. Traffic light එකක් ළඟ නැවතුණොත් ඊට වඩා වැඩි වෙලාවක් ගාස්තු නෑ. Traffic light තියෙන තැන් © OpenStreetMap.",
    freeStopLabel = "සාමාන්‍ය නැවතීම (විනාඩි)",
    lightNote = "🚦 Traffic light · %s\nවිනාඩි %s දක්වා ගාස්තු නෑ",
    lightMarkedNote = "🚦 Traffic light · %s\nමේ නැවතීමට ගාස්තු නෑ",
    markLight = "🚦 Signal එකක්",
    signalFreeLabel = "Traffic light (විනාඩි)",
    stoppedNote = "🚦 නවත්වලා · %s\nවිනාඩි %s ට වැඩි වුණොත් රැඳී සිටීම ගණන් වෙනවා",
    adjustAllTitle = "සියලු මිල එක පාරින් වෙනස් කරන්න",
    minFareLine = "අවම ගාස්තුව %s",
    minFareLabel = "අවම ගාස්තුව (0 = නැත)", minFareTopUp = "අවම ගාස්තුවට එකතු කළ මුදල",
    adjustAllHint = "ප්‍රතිශතය (උදා. 2 හෝ 3) දාලා − හෝ + ඔබන්න. හැම වාහනයකම මිල ගණන් ඒ ප්‍රමාණයෙන් අඩු / වැඩි වෙනවා.",
    payCardTitle = "📱 QR ගෙවීම් (ඔබේ ගිණුම)",
    payCardHint = "ඔබේ බැංකුවෙන් / payment app එකෙන් දුන්න QR එකේ පින්තූරය මෙතනට දාන්න. බිල් එකේදී customer ට scan කරන්න පෙන්නනවා.",
    pickQr = "QR පින්තූරය තෝරන්න", changeQr = "QR එක වෙනස් කරන්න", remove = "අයින් කරන්න",
    accountLabel = "ගිණුම් විස්තර (නම, බැංකුව, ගිණුම් අංකය)", badImage = "ඒ පින්තූරය කියවන්න බැරි වුණා",
    tripsTitle = "ගමන්", todayIncome = "අද ආදායම · ගමන් %d", noTrips = "තාම ගමන් නෑ.",
    tripsKeptNote = "ගමනක් ඔබලා විස්තර බලන්න. ගමන් දවස් %d කට පස්සේ ඉබේම මැකෙනවා.",
    deleteTrip = "මේ ගමන මකන්න", deleteTripTitle = "මේ ගමන මකන්නද?",
    clearTrips = "ගමන් ලැයිස්තුව මකන්න", clearConfirmTitle = "ගමන් ඔක්කොම මකන්නද?",
    clearConfirmText = "මේක ආයේ ගන්න බෑ.", delete = "මකන්න", cancel = "එපා",
    notifText = "ගමන මනිමින් පවතී", language = "භාෂාව",
    pauseTrip = "හයර් එක අතරතුර Uber / PickMe හයර් යෑම",
    resumeTrip = "නැවත හයරයට පැමිණ, Uber / PickMe හයර් එක ඉවර කිරීම",
    pauseConfirmTitle = "Uber / PickMe හයර් එකකට යනවද?",
    pauseConfirmText = "ආයෙ මේ හයරයට එනකල් මේ හයරයේ දුර සහ රැඳී සිටීම ගණන් වෙන්නේ නෑ.",
    pauseConfirmYes = "ඔව්, යනවා",
    otherHiresTitle = "🚗 හයර් එක අතරතුර Uber / PickMe හයර්",
    otherHiresHint = "On කළාම මීටරයේ \"හයර් එක අතරතුර Uber / PickMe හයර් යෑම\" button එක පේනවා. Customer බලාගෙන ඉන්න අතරේ වෙන හයර් යනකොට, මේ හයරයේ මීටරය ඒ වෙලාවට නවත්වනවා.",
    endConfirmTitle = "ගමන ඉවර කරන්නද?",
    tripRecovered = "⚠️ නැවතිලා තිබුණු ගමන ආයෙ ලබාගත්තා. App එක නැවතිලා තිබුණු වෙලාවේ දුර අඩුවෙන් ගණන් වෙන්න පුළුවන්.",
    batteryTitle = "🔋 Battery",
    batteryHint = "Battery saver එකෙන් ගමනක් යනකොට app එක නවත්වන්න පුළුවන්. මේක ඔබලා HireMeter එකට battery limit අයින් කරන්න. Samsung phone වල Settings → Battery → Background usage limits එකේ \"Sleeping apps\" වලිනුත් HireMeter අයින් කරන්න.",
    autostartHint = "Xiaomi / Redmi phone වල app එක නැවතුණොත් මීටරය ඉබේම ආයෙ පටන් ගන්නේ නෑ. පහළ button එක ඔබලා HireMeter එකට \"Autostart\" on කරන්න.",
    autostartButton = "Autostart on කරන්න",
    batteryButton = "Battery limit අයින් කරන්න", batteryDone = "✓ HireMeter එකට battery limit නෑ",
    pausedBanner = "🚗 Uber / PickMe හයර් එකක · %s\nමේ හයරයේ දුර සහ කාලය ගණන් වෙන්නේ නෑ. GPS එක off.",
    pausedTime = "Uber / PickMe හයර් ගිය කාලය (ගාස්තු නැත)", notifPaused = "Uber / PickMe හයර් එකක - මේ හයරයේ මීටරය නවත්වා ඇත",
    pausedCharged = "රැඳී සිටි කාලය (Uber / PickMe හයර් ගිය)", waitingFareLabel = "රැඳී සිටීමට ගාස්තුව", waitRateLabel = "රැඳීම / විනාඩියකට", waitedLabel = "රැඳී සිටි කාලය",
    chargePausedLabel = "Uber / PickMe හයර් ගිය කාලයටත් රැඳී සිටීමේ ගාස්තුව ගන්න · විනාඩියට %s",
    viewOnGoogle = "🗺️ Google Maps එකෙන් බලන්න", routeLink = "ගිය පාර:",
    backupTitle = "☁️ Backup", driveTurnOn = "☁️ Google Drive backup on කරන්න",
    driveHint = "Save කරන්න තැන අහනකොට ☰ menu එකෙන් \"Drive\" තෝරන්න. මේක එක පාරක් කළාම ඇති. ඊට පස්සේ app එක ඉබේම backup කරනවා.",
    driveOnLine = "☁️ Google Drive backup: on · අන්තිම backup එක %s",
    driveFailed = "⚠️ අන්තිම backup එක අසාර්ථකයි: %s",
    backupNow = "දැන් backup කරන්න", restoreFromDrive = "Drive එකෙන් ආයෙ ගන්න", driveTurnOff = "Drive backup off කරන්න",
    fileBackup = "Backup එක file එකක් විදියට", makeBackupFile = "Backup file එකක් හදන්න", restoreFile = "File එකෙන් ආයෙ ගන්න",
    fileSaved = "✅ Backup file එක save වුණා.",
    restoreTitle = "Backup එකෙන් ආයෙ ගන්නද?",
    restoreText = "මේ backup එකේ ගමන් %d ක් තියෙනවා. මේ phone එකේ ගමන්, මිල ගණන්, QR සහ settings ඔක්කොම backup එකේ තියෙන ඒවට මාරු වෙනවා. දැන් තියෙන ඒවා ආයෙ ගන්න බෑ.",
    restoreYes = "ඔව්, ආයෙ ගන්න", restoredMsg = "✅ Backup එකෙන් ආයෙ ගත්තා: ගමන් %d",
    backingUp = "☁️ Backup කරනවා...", backedUp = "✅ Drive එකට backup වුණා.",
    driveDone = "✅ Google Drive backup on. දැන් ඉඳන් ඉබේම backup වෙනවා.",
    driveExisting = "☁️ මේ Drive file එකේ කලින් backup එකක් තියෙනවා.",
    driveNotLinked = "Drive backup on වුණේ නෑ. Drive එකේ තියෙන පරණ backup එක වෙනස් වුණේ නෑ. අලුත් file එකක් තෝරන්න.",
    restoreLocked = "ගමනක් යන අතරතුර backup එකෙන් ආයෙ ගන්න බෑ.", notABackup = "මේක HireMeter backup එකක් නෙවෙයි",
    backupFailed = "❌ අසාර්ථකයි: %s",
    tuk = "ත්‍රීවීල්", bike = "බයික්", mini = "මිනි කාර්", flex = "ෆ්ලෙක්ස්", car = "කාර්", minivan = "මිනි වෑන්", van = "වෑන්",
)

private val TAMIL = Strings(
    currency = "ரூ.",
    tabMeter = "மீட்டர்", tabSpeed = "வேகம்",
    speedTitle = "வாகன வேகம்", topSpeed = "அதிகபட்ச வேகம்", resetTopSpeed = "மீண்டும் தொடங்கு",
    speedNote = "வேகம் GPS மூலம் அளக்கப்படுகிறது; உண்மையான வேகத்திலிருந்து சற்று வேறுபடலாம். மீட்டர் கட்டணத்தை இது பாதிக்காது.",
    allowLocation = "Location அனுமதி கொடு",
    tabRates = "கட்டணங்கள்", tabTrips = "பயணங்கள்", tabSettings = "அமைப்புகள்",
    date = "தேதி", distanceFare = "தூரக் கட்டணம்", timeFare = "நேரக் கட்டணம்",
    fare = "கட்டணம்", distance = "தூரம்", elapsed = "மொத்த நேரம்",
    startTrip = "பயணத்தைத் தொடங்கு", endTrip = "பயணத்தை முடி",
    gpsSearching = "📡 GPS சிக்னலைத் தேடுகிறது… திறந்த வெளிக்குச் செல்லவும்",
    rateLine = "முதல் km %s · அடுத்த km %s · காத்திருப்பு நிமிடத்திற்கு %s",
    fareSplit = "தூரத்திற்கு %s + காத்திருப்புக்கு %s",
    meterNote = "கட்டணம் = தூரம் (km-க்கு) + காத்திருந்த நேரம் (நிமிடத்திற்கு). சிக்னல் போன்ற குறுகிய நிறுத்தங்களுக்குக் கட்டணம் இல்லை.",
    gpsOff = "Location (GPS)-ஐ இயக்கவும்", noPermission = "Location அனுமதி இல்லாமல் தூரத்தை அளக்க முடியாது",
    billTitle = "பயண பில்", total = "மொத்தக் கட்டணம்", vehicleLabel = "வாகனம்", ok = "சரி", sendBill = "பில்லை அனுப்பு",
    payByQr = "📱 QR மூலம் செலுத்து", payTitle = "QR-ஐ ஸ்கேன் செய்து செலுத்தவும்", amountDue = "செலுத்த வேண்டிய தொகை",
    payHint = "வங்கி app மூலம் ஸ்கேன் செய்து, மேலே உள்ள தொகையை உள்ளிடவும்.",
    ratesTitle = "கட்டணங்கள் (ரூபாய்)", ratesLocked = "பயணத்தின் போது கட்டணங்களை மாற்ற முடியாது.",
    ratesHint = "உங்கள் கட்டணங்களை இங்கே உள்ளிடவும். தட்டச்சு செய்யும்போதே சேமிக்கப்படும்.",
    firstKm = "முதல் km", nextKm = "அடுத்த km-க்கு",
    freeStopTitle = "⏱️ காத்திருப்பு எப்போது கணக்கிடப்படும்?",
    freeStopHint = "வாகனம் இத்தனை நிமிடங்களுக்கு மேல் நின்றால், அந்த நிறுத்தம் தொடக்கத்திலிருந்தே காத்திருப்பாகக் கணக்கிடப்படும். அதைவிடக் குறுகிய நிறுத்தங்களுக்கு (சிக்னல், நெரிசல்) கட்டணம் இல்லை. பயணத்தின் முதல் 700 மீட்டர் வரை காத்திருப்பு கணக்கிடப்படாது. சிக்னல் அருகே நின்றால் இதைவிட அதிக நேரம் கட்டணம் இல்லை. சிக்னல் இடங்கள் © OpenStreetMap.",
    freeStopLabel = "சாதாரண நிறுத்தம் (நிமிடங்கள்)",
    lightNote = "🚦 சிக்னல் · %s\n%s நிமிடங்கள் வரை கட்டணம் இல்லை",
    lightMarkedNote = "🚦 சிக்னல் · %s\nஇந்த நிறுத்தத்துக்குக் கட்டணம் இல்லை",
    markLight = "🚦 சிக்னல்",
    signalFreeLabel = "சிக்னல் (நிமிடங்கள்)",
    stoppedNote = "🚦 நின்றுள்ளது · %s\n%s நிமிடங்களுக்கு மேல் நின்றால் காத்திருப்பு கணக்கிடப்படும்",
    adjustAllTitle = "எல்லாக் கட்டணங்களையும் ஒரே முறையில் மாற்று",
    minFareLine = "குறைந்தபட்சக் கட்டணம் %s",
    minFareLabel = "குறைந்தபட்சக் கட்டணம் (0 = இல்லை)", minFareTopUp = "குறைந்தபட்சக் கட்டணத்திற்காகச் சேர்த்தது",
    adjustAllHint = "சதவீதத்தை (எ.கா. 2 அல்லது 3) உள்ளிட்டு − அல்லது + அழுத்தவும். எல்லா வாகனங்களின் கட்டணங்களும் அந்த அளவு குறையும் / கூடும்.",
    payCardTitle = "📱 QR கட்டணம் (உங்கள் கணக்கு)",
    payCardHint = "உங்கள் வங்கி / payment app வழங்கிய QR படத்தை இங்கே சேர்க்கவும். பில்லில் வாடிக்கையாளர் ஸ்கேன் செய்யக் காட்டப்படும்.",
    pickQr = "QR படத்தைத் தேர்ந்தெடு", changeQr = "QR-ஐ மாற்று", remove = "நீக்கு",
    accountLabel = "கணக்கு விவரங்கள் (பெயர், வங்கி, கணக்கு எண்)", badImage = "அந்தப் படத்தைப் படிக்க முடியவில்லை",
    tripsTitle = "பயணங்கள்", todayIncome = "இன்றைய வருமானம் · பயணங்கள் %d", noTrips = "இன்னும் பயணங்கள் இல்லை.",
    tripsKeptNote = "விவரங்களைப் பார்க்க ஒரு பயணத்தைத் தொடுங்கள். பயணங்கள் %d நாட்களுக்குப் பிறகு தானாக அழியும்.",
    deleteTrip = "இந்தப் பயணத்தை அழி", deleteTripTitle = "இந்தப் பயணத்தை அழிக்கவா?",
    clearTrips = "பயணப் பட்டியலை அழி", clearConfirmTitle = "எல்லாப் பயணங்களையும் அழிக்கவா?",
    clearConfirmText = "இதை மீட்டெடுக்க முடியாது.", delete = "அழி", cancel = "வேண்டாம்",
    notifText = "பயணம் அளக்கப்படுகிறது", language = "மொழி",
    pauseTrip = "இந்தப் பயணத்தின் இடையில் Uber / PickMe பயணம் செல்",
    resumeTrip = "மீண்டும் இந்தப் பயணத்துக்கு வந்தேன், Uber / PickMe பயணம் முடிந்தது",
    pauseConfirmTitle = "Uber / PickMe பயணத்துக்குச் செல்கிறீர்களா?",
    pauseConfirmText = "இந்தப் பயணத்துக்குத் திரும்பி வரும் வரை இதன் தூரமும் காத்திருப்பும் கணக்கிடப்படாது.",
    pauseConfirmYes = "ஆம், செல்கிறேன்",
    otherHiresTitle = "🚗 பயணத்தின் இடையில் Uber / PickMe",
    otherHiresHint = "இயக்கினால் மீட்டரில் \"இடையில் Uber / PickMe பயணம் செல்\" பொத்தான் தெரியும். வாடிக்கையாளர் காத்திருக்கும் போது வேறு பயணம் சென்றால், அந்த நேரம் இந்தப் பயணத்தின் மீட்டர் நிறுத்தப்படும்.",
    endConfirmTitle = "பயணத்தை முடிக்கவா?",
    tripRecovered = "⚠️ நின்றுபோன பயணம் மீட்டெடுக்கப்பட்டது. App நின்றிருந்த நேரத்தின் தூரம் குறைவாகக் கணக்கிடப்படலாம்.",
    batteryTitle = "🔋 Battery",
    batteryHint = "Battery saver பயணத்தின் போது app-ஐ நிறுத்தக்கூடும். இதை அழுத்தி HireMeter-க்கு battery வரம்பை நீக்கவும். Samsung போன்களில் Settings → Battery → Background usage limits-இல் \"Sleeping apps\" பட்டியலிலிருந்தும் HireMeter-ஐ நீக்கவும்.",
    autostartHint = "Xiaomi / Redmi போன்களில் app நின்றால் மீட்டர் தானாக மீண்டும் தொடங்காது. கீழே உள்ள பொத்தானை அழுத்தி HireMeter-க்கு \"Autostart\"-ஐ இயக்கவும்.",
    autostartButton = "Autostart-ஐ இயக்கு",
    batteryButton = "Battery வரம்பை நீக்கு", batteryDone = "✓ HireMeter-க்கு battery வரம்பு இல்லை",
    pausedBanner = "🚗 Uber / PickMe பயணத்தில் · %s\nஇந்தப் பயணத்தின் தூரமும் நேரமும் கணக்கிடப்படாது. GPS அணைக்கப்பட்டுள்ளது.",
    pausedTime = "Uber / PickMe பயண நேரம் (கட்டணம் இல்லை)", notifPaused = "Uber / PickMe பயணத்தில் - இந்த மீட்டர் நிறுத்தப்பட்டுள்ளது",
    pausedCharged = "காத்திருந்த நேரம் (Uber / PickMe பயணம்)", waitingFareLabel = "காத்திருப்புக் கட்டணம்", waitRateLabel = "காத்திருப்பு / நிமிடம்", waitedLabel = "காத்திருந்த நேரம்",
    chargePausedLabel = "Uber / PickMe பயண நேரத்துக்கும் காத்திருப்புக் கட்டணம் · நிமிடத்திற்கு %s",
    viewOnGoogle = "🗺️ Google Maps-இல் பார்க்க", routeLink = "சென்ற பாதை:",
    backupTitle = "☁️ Backup", driveTurnOn = "☁️ Google Drive backup-ஐ இயக்கு",
    driveHint = "எங்கே சேமிக்க என்று கேட்கும் போது ☰ மெனுவில் \"Drive\"-ஐத் தேர்ந்தெடுங்கள். ஒரு முறை செய்தால் போதும். பிறகு app தானாக backup செய்யும்.",
    driveOnLine = "☁️ Google Drive backup: இயக்கத்தில் · கடைசி backup %s",
    driveFailed = "⚠️ கடைசி backup தோல்வி: %s",
    backupNow = "இப்போது backup செய்", restoreFromDrive = "Drive-இலிருந்து மீட்டெடு", driveTurnOff = "Drive backup-ஐ நிறுத்து",
    fileBackup = "Backup-ஐ ஒரு கோப்பாக", makeBackupFile = "Backup கோப்பு உருவாக்கு", restoreFile = "கோப்பிலிருந்து மீட்டெடு",
    fileSaved = "✅ Backup கோப்பு சேமிக்கப்பட்டது.",
    restoreTitle = "Backup-இலிருந்து மீட்டெடுக்கவா?",
    restoreText = "இந்த backup-இல் %d பயணங்கள் உள்ளன. இந்த phone-இன் பயணங்கள், கட்டணங்கள், QR மற்றும் அமைப்புகள் அனைத்தும் backup-இல் உள்ளவையாக மாறும். இப்போதுள்ளவற்றைத் திரும்பப் பெற முடியாது.",
    restoreYes = "ஆம், மீட்டெடு", restoredMsg = "✅ Backup-இலிருந்து மீட்டெடுக்கப்பட்டது: %d பயணங்கள்",
    backingUp = "☁️ Backup செய்கிறது...", backedUp = "✅ Drive-இல் backup செய்யப்பட்டது.",
    driveDone = "✅ Google Drive backup இயக்கப்பட்டது. இனி தானாக backup ஆகும்.",
    driveExisting = "☁️ இந்த Drive கோப்பில் ஏற்கனவே ஒரு backup உள்ளது.",
    driveNotLinked = "Drive backup இயக்கப்படவில்லை. Drive-இல் உள்ள பழைய backup மாறவில்லை. புதிய கோப்பைத் தேர்ந்தெடுங்கள்.",
    restoreLocked = "பயணம் நடக்கும் போது backup-இலிருந்து மீட்டெடுக்க முடியாது.", notABackup = "இது HireMeter backup அல்ல",
    backupFailed = "❌ தோல்வி: %s",
    tuk = "முச்சக்கர வண்டி", bike = "பைக்", mini = "மினி கார்", flex = "ஃப்ளெக்ஸ்", car = "கார்", minivan = "மினி வேன்", van = "வேன்",
)

private val ENGLISH = Strings(
    currency = "Rs.",
    tabMeter = "Meter", tabSpeed = "Speed",
    speedTitle = "Vehicle speed", topSpeed = "Top speed", resetTopSpeed = "Reset",
    speedNote = "Speed comes from the GPS and can differ a little from the real speed. It does not change the meter's fare.",
    allowLocation = "Allow location",
    tabRates = "Rates", tabTrips = "Trips", tabSettings = "Settings",
    date = "Date", distanceFare = "Distance fare", timeFare = "Time fare",
    fare = "Fare", distance = "Distance", elapsed = "Trip time",
    startTrip = "Start trip", endTrip = "End trip",
    gpsSearching = "📡 Looking for GPS signal… move into the open",
    rateLine = "First km %s · next km %s · waiting %s per minute",
    fareSplit = "Distance %s + waiting %s",
    meterNote = "Fare = distance (per km) + waiting time (per minute). Short stops like traffic lights are free.",
    gpsOff = "Turn on Location (GPS)", noPermission = "Distance cannot be measured without the Location permission",
    billTitle = "Trip bill", total = "Total fare", vehicleLabel = "Vehicle", ok = "OK", sendBill = "Send bill",
    payByQr = "📱 Pay by QR", payTitle = "Scan the QR to pay", amountDue = "Amount to pay",
    payHint = "Scan with your bank app and enter the amount shown above.",
    ratesTitle = "Rates (rupees)", ratesLocked = "Rates cannot be changed during a trip.",
    ratesHint = "Enter your own rates here. They are saved as you type.",
    firstKm = "First km", nextKm = "Each next km",
    freeStopTitle = "⏱️ When waiting starts",
    freeStopHint = "A stop longer than this many minutes is charged as waiting from its start. Shorter stops (traffic lights, jams) are free. No waiting is counted in the first 700 m of a hire. A stop at a traffic light is free for longer. Traffic light places © OpenStreetMap.",
    freeStopLabel = "Ordinary stop (minutes)",
    lightNote = "🚦 Traffic light · %s\nFree up to %s min",
    lightMarkedNote = "🚦 Traffic light · %s\nThis stop is not charged",
    markLight = "🚦 It's a light",
    signalFreeLabel = "Traffic light (minutes)",
    stoppedNote = "🚦 Stopped · %s\nCharged as waiting after %s min",
    adjustAllTitle = "Change all rates at once",
    minFareLine = "Minimum fare %s",
    minFareLabel = "Minimum fare (0 = none)", minFareTopUp = "Added to reach minimum fare",
    adjustAllHint = "Type a percentage (e.g. 2 or 3), then press − or +. Every vehicle's rates go down / up by that much.",
    payCardTitle = "📱 QR payments (your account)",
    payCardHint = "Add the picture of the QR your bank or payment app gave you. It is shown on the bill for the customer to scan.",
    pickQr = "Choose QR picture", changeQr = "Change QR", remove = "Remove",
    accountLabel = "Account details (name, bank, account number)", badImage = "That picture could not be read",
    tripsTitle = "Trips", todayIncome = "Today's income · %d trips", noTrips = "No trips yet.",
    tripsKeptNote = "Tap a trip to see its details. Trips are deleted automatically after %d days.",
    deleteTrip = "Delete this trip", deleteTripTitle = "Delete this trip?",
    clearTrips = "Clear trip list", clearConfirmTitle = "Delete all trips?",
    clearConfirmText = "This cannot be undone.", delete = "Delete", cancel = "Cancel",
    notifText = "Measuring the trip", language = "Language",
    pauseTrip = "Go on Uber / PickMe hires during this hire",
    resumeTrip = "Back to this hire, Uber / PickMe hire done",
    pauseConfirmTitle = "Going on an Uber / PickMe hire?",
    pauseConfirmText = "This hire's distance and waiting are not counted until you come back to it.",
    pauseConfirmYes = "Yes, going",
    otherHiresTitle = "🚗 Uber / PickMe hires in between",
    otherHiresHint = "When on, the meter shows a \"Go on Uber / PickMe hires during this hire\" button. While the customer waits and you do other hires, this hire's meter stops for that time.",
    endConfirmTitle = "End the trip?",
    tripRecovered = "⚠️ The stopped trip was recovered. Distance while the app was stopped may be counted short.",
    batteryTitle = "🔋 Battery",
    batteryHint = "Battery saving can stop the app during a trip. Tap below to take HireMeter off battery limits. On Samsung phones, also remove HireMeter from \"Sleeping apps\" (Settings → Battery → Background usage limits).",
    autostartHint = "On Xiaomi / Redmi phones the meter does not restart by itself if the app is stopped. Tap below and turn on \"Autostart\" for HireMeter.",
    autostartButton = "Turn on Autostart",
    batteryButton = "Remove battery limit", batteryDone = "✓ HireMeter has no battery limit",
    pausedBanner = "🚗 On an Uber / PickMe hire · %s\nThis hire's distance and time are not counted. GPS is off.",
    pausedTime = "Away on Uber / PickMe hires (not charged)", notifPaused = "On an Uber / PickMe hire - this meter is stopped",
    pausedCharged = "Waited (away on Uber / PickMe hires)", waitingFareLabel = "Waiting fare", waitRateLabel = "Waiting / min", waitedLabel = "Waiting time",
    chargePausedLabel = "Charge the Uber / PickMe hire time as waiting too · %s per minute",
    viewOnGoogle = "🗺️ View on Google Maps", routeLink = "Route:",
    backupTitle = "☁️ Backup", driveTurnOn = "☁️ Turn on Google Drive backup",
    driveHint = "When it asks where to save, pick \"Drive\" from the ☰ menu. Do this once. After that the app backs up by itself.",
    driveOnLine = "☁️ Google Drive backup: on · last backup %s",
    driveFailed = "⚠️ Last backup failed: %s",
    backupNow = "Back up now", restoreFromDrive = "Restore from Drive", driveTurnOff = "Turn off Drive backup",
    fileBackup = "Backup as a file", makeBackupFile = "Make a backup file", restoreFile = "Restore from a file",
    fileSaved = "✅ Backup file saved.",
    restoreTitle = "Restore from the backup?",
    restoreText = "This backup has %d trips. The trips, rates, QR and settings on this phone will all change to the ones in the backup. The ones here now cannot be got back.",
    restoreYes = "Yes, restore", restoredMsg = "✅ Restored from the backup: %d trips",
    backingUp = "☁️ Backing up...", backedUp = "✅ Backed up to Drive.",
    driveDone = "✅ Google Drive backup is on. From now on it backs up by itself.",
    driveExisting = "☁️ This Drive file already has a backup.",
    driveNotLinked = "Drive backup is not on. The old backup on Drive is not changed. Pick a new file.",
    restoreLocked = "Restoring is not possible during a trip.", notABackup = "This is not a HireMeter backup",
    backupFailed = "❌ Failed: %s",
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
