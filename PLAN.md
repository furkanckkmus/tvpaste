# TVPaste Proje Planı (OpenCode için)

Bu dosyayı projenin ana klasörüne koy. OpenCode'a her aşamada şunu söyle:
"PLAN.md dosyasını oku. Sadece AŞAMA X'i yap, bitirince dur."

---

## 0. GENEL KURALLAR (her aşamada geçerli, asla çiğneme)

1. Aynı anda SADECE bir aşama yap. Aşama bitince dur ve bana testleri söyle. Ben "tamam" demeden sonraki aşamaya geçme.
2. Dışarıdan kütüphane (dependency) EKLEME. Sadece Android SDK ve Kotlin standart kütüphanesi kullan. HTTP sunucusu için `java.net.ServerSocket`, istemci için `HttpURLConnection` kullan.
3. Kodu küçük dosyalara böl. Hiçbir dosya 200 satırı geçmesin.
4. Her aşamanın sonunda şu komutla derle ve hata varsa kendin düzelt: `./gradlew assembleDebug`
   (Windows'ta `gradlew.bat assembleDebug`). Derleme geçmeden aşamayı bitmiş sayma.
5. Emin olmadığın bir Android API'si varsa uydurma. Bana sor veya en basit çalışan yolu kullan.
6. Bana programlama bilmeyen biri olarak anlat. Teknik terimleri kısa açıkla. Test adımlarını numaralı liste ile ver.
7. Mevcut çalışan kodu gereksiz yere yeniden yazma. Sadece gereken değişikliği yap.
8. Metin hep UTF-8 olsun (Türkçe karakterler bozulmasın).

---

## 1. PROJE ÖZETİ

Telefondaki bir yazıyı (metin, kelime, URL) tek tuşla Android TV'de o an odaklanılmış metin alanına yazan sistem.

İki Android uygulaması var, tek Gradle projesi içinde iki modül:

| Modül | Paket adı | Cihaz | Görevi |
|---|---|---|---|
| `tv` | `com.tvpaste.tv` | TCL Android TV (Android 14, API 34) | Erişilebilirlik servisi + küçük HTTP sunucusu + ağda kendini duyurma |
| `phone` | `com.tvpaste.phone` | Android telefon | TV'yi ağda bulur, yazıyı gönderir |

Genel akış:
`Telefon uygulaması --HTTP POST /send--> TV'deki sunucu --> Erişilebilirlik servisi --> odaklı metin alanına yazar`

Karar verilmiş şeyler (değiştirme):
- Aynı Wi-Fi ağında çalışır. İnternet, bulut, aracı sunucu YOK.
- PIN / şifre YOK (ilk sürüm). Ama kodda ileride eklenebilsin diye tek bir yerde `isAuthorized(request)` fonksiyonu olsun, şimdilik hep `true` dönsün.
- TV'nin IP'si değişebilir. Bu yüzden telefon TV'yi NsdManager (mDNS) ile bulur. Yedek olarak TV ekranında IP gösterilir ve telefonda elle IP girilebilir.
- Yazma davranışı: alandaki mevcut metnin imleç konumuna EKLE (silme).
- Sunucu portu: 8080. Servis tipi: `_tvpaste._tcp.`
- Dil: Kotlin. Arayüz: Basit XML veya düz Android View (Compose KULLANMA).
- TV: minSdk 28, targetSdk 34. Telefon: minSdk 26, targetSdk 34.
- Telefon ve TV uygulamalarının `AndroidManifest.xml` dosyasında `android:usesCleartextTraffic="true"` olsun (yerel ağda HTTPS yok).

HTTP protokolü (çok basit):
- `GET /ping` → `200`, gövde: `tvpaste-ok`
- `POST /send` → gövde: UTF-8 düz metin. Başarılıysa `200 OK`, odaklı alan yoksa `409` ve gövdede `no-focused-field`, diğer hata `500`.
- Sunucu istekleri tek tek, ayrı bir thread'de işler. Ana (UI) thread'i bloklamaz.

---

## AŞAMA 0: Proje iskeleti + TV'de sabit metin yazan servis

Bu projenin EN RİSKLİ kısmı. Önce bunu doğrulayacağız.

Yapılacaklar:
1. Boş bir Gradle projesi oluştur (Kotlin DSL). `tv` ve `phone` modüllerini tanımla. Bu aşamada `phone` sadece boş açılan bir Activity olsun.
2. `tv` modülünde:
   - `MainActivity`: Ekranda büyük yazıyla "TVPaste hazır" ve altında "Erişilebilirlik servisi: AÇIK/KAPALI" göstersin. Servisin açık olup olmadığını `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` ile kontrol et. Manifest'te `LEANBACK_LAUNCHER` kategorisi olsun ve `<uses-feature android:name="android.software.leanback" android:required="false"/>`, `<uses-feature android:name="android.hardware.touchscreen" android:required="false"/>` ekle.
   - `PasteService : AccessibilityService`: `res/xml/accessibility_config.xml` dosyası ile tanımlı. `canRetrieveWindowContent="true"`, `flagRetrieveInteractiveWindows` ve `flagIncludeNotImportantViews` ve `flagReportViewIds` açık olsun. Servis bağlanınca (`onServiceConnected`) logcat'e "PasteService baglandi" yazsın.
   - `TextInjector.kt`: Tek bir fonksiyon: `fun inject(service: AccessibilityService, text: String): Boolean`
     Mantık:
     a. `service.rootInActiveWindow` al. Yoksa `false`.
     b. Önce `findFocus(AccessibilityNodeInfo.FOCUS_INPUT)` dene. Bulunamazsa ağacı gez, `isEditable == true` ve `isFocused == true` olan düğümü bul. Hâlâ yoksa `false`.
     c. Düğümün mevcut metnini al (`node.text`, boşsa ""). İpucu (hint) metnine denk geliyorsa (`node.isShowingHintText == true`) mevcut metni "" say.
     d. İmleç konumunu `textSelectionStart` / `textSelectionEnd` ile al (-1 ise sona ekle). Yeni metin = önceki kısım + eklenen metin + sonraki kısım.
     e. `ACTION_SET_TEXT` ile yeni metni yaz. Sonra imleci eklenen metnin sonuna taşı (`ACTION_SET_SELECTION`).
     f. `ACTION_SET_TEXT` başarısız dönerse YEDEK yöntem: metni `ClipboardManager` ile panoya koy, sonra düğümde `ACTION_PASTE` çağır.
     g. Başarılıysa `true`.
3. Bu aşamada test için, `PasteService` bağlandıktan 10 saniye sonra bir kere `TextInjector.inject(this, "TEST123")` çağırsın (geçici kod, `// TODO: aşama 1'de sil` yorumuyla işaretle).
4. `docs/ADB_KOMUTLARI.md` dosyası oluştur. İçinde şunlar olsun:
   - Kablosuz ADB bağlantısı: `adb connect TV_IP:5555`
   - Kurulum: `adb install -r tv/build/outputs/apk/debug/tv-debug.apk`
   - Servisi komutla açma: `adb shell settings put secure enabled_accessibility_services com.tvpaste.tv/com.tvpaste.tv.PasteService` ve `adb shell settings put secure accessibility_enabled 1`
   - Log izleme: `adb logcat -s PasteService`

BEN ŞUNLARI TEST EDECEĞİM (bana bu listeyi ver):
1. APK'yı TV'ye kur, uygulama açılsın.
2. Servisi ADB komutuyla aç, ekranda "AÇIK" yazsın.
3. TV'de bir tarayıcı veya arama kutusunda bir yazı alanına tıkla (klavye açılsın), 10 saniye bekle, alana "TEST123" yazılsın.
4. Çalışmazsa logcat çıktısını sana vereceğim.

---

## AŞAMA 1: TV'de HTTP sunucusu

Yapılacaklar:
1. Aşama 0'daki geçici 10 saniyelik test kodunu sil.
2. `SimpleHttpServer.kt`: `ServerSocket` ile port 8080'de dinleyen basit sunucu. Her bağlantıyı ayrı thread'de işle. Sadece `GET /ping` ve `POST /send` desteklesin. `Content-Length` başlığına göre gövdeyi tam oku, UTF-8 çöz. Sınır: gövde en fazla 100 KB.
3. Sunucu `PasteService` içinde başlasın (`onServiceConnected`) ve servis kapanınca (`onUnbind` / `onDestroy`) durdursun. `PasteService` bir statik/singleton referans tutmasın, sunucuya lambda ver: `onText: (String) -> Boolean`.
4. `/send` isteği gelince `TextInjector.inject` çağrılsın. Sonuca göre 200 veya 409 dön. Alan işlemi için gerekirse `Handler(Looper.getMainLooper())` kullan ve sonucu bekle (en fazla 2 sn).
5. `isAuthorized(request)` fonksiyonu ekle, şimdilik hep `true`.
6. Her istek logcat'e yazılsın (metnin kendisini DEĞİL, sadece uzunluğunu yaz).

TEST:
1. TV'de bir metin alanına tıkla.
2. Bilgisayardan: `curl -X POST --data "merhaba şğüöçı" http://TV_IP:8080/send` → TV'de alana yazı gelmeli, Türkçe karakterler bozulmamalı.
3. `curl http://TV_IP:8080/ping` → `tvpaste-ok`
4. Alan seçili değilken `/send` → `409` dönmeli.
5. Alanda önceden yazı varken ve imleç ortadayken gönder → yazı imlecin olduğu yere eklenmeli.

---

## AŞAMA 2: TV'nin kendini ağda duyurması + IP gösterme

Yapılacaklar:
1. `NsdAnnouncer.kt`: `NsdManager.registerService` ile servis tipi `_tvpaste._tcp.`, isim `TVPaste`, port 8080 olarak duyur. Servis başlayınca kaydet, durunca `unregisterService` yap. Manifest'e `INTERNET`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE` izinlerini ekle.
2. TV `MainActivity` ekranında şunlar görünsün (büyük yazı, uzaktan okunabilir):
   - Erişilebilirlik servisi durumu
   - Sunucu durumu (çalışıyor / çalışmıyor): `/ping` isteğini kendi `127.0.0.1:8080` adresine atarak kontrol et.
   - TV'nin yerel IP adresi ve port: örn. `192.168.1.25:8080`. IP'yi `NetworkInterface` listesinden IPv4 olarak bul.
   - Servis kapalıysa kısa bir uyarı: "Erişilebilirlik ayarlarından TVPaste'i açın."
3. Ekran her 3 saniyede bir kendini yenilesin.

TEST:
1. TV uygulaması IP'yi doğru göstermeli (router'daki cihaz listesiyle kıyasla).
2. Telefonda (Play Store'dan indirilen herhangi bir "Service Browser"/"Bonjour Browser" uygulamasıyla) `_tvpaste._tcp` servisi görünmeli. Görünmezse bile Aşama 3'te elle IP ile devam edebiliriz.

---

## AŞAMA 3: Telefon uygulaması

Yapılacaklar:
1. `phone` modülünde tek ekranlı `MainActivity`:
   - Üstte durum yazısı: "TV aranıyor..." / "TV bulundu: 192.168.1.25" / "TV bulunamadı".
   - Çok satırlı metin kutusu.
   - "Panodan Yapıştır" butonu (panodaki metni kutuya koyar).
   - "Temizle" butonu.
   - Büyük "GÖNDER" butonu.
   - Altta küçük "Elle IP gir" alanı (ör. `192.168.1.25`). Girilen IP `SharedPreferences`'e kaydedilsin. Elle IP girilmişse keşif yerine o kullanılsın.
2. `TvDiscovery.kt`: `NsdManager.discoverServices("_tvpaste._tcp.")`. Bulunan servisi `resolveService` ile çöz, IP ve portu al. Android'in aynı anda birden fazla resolve'u kabul etmediğini unutma: resolve işlemlerini sırayla yap. Uygulama kapanınca (`onDestroy`) keşfi durdur.
3. `TvClient.kt`: `HttpURLConnection` ile `POST http://IP:PORT/send`, gövde UTF-8, `Content-Type: text/plain; charset=utf-8`, bağlantı zaman aşımı 3 sn. Arka plan thread'inde çalışsın (UI thread'inde ASLA ağ işlemi yapma). Sonuç dönsün: başarı / alan yok (409) / ulaşılamadı.
4. Gönder'e basınca sonuç kullanıcıya Toast ile gösterilsin:
   - 200 → "Gönderildi"
   - 409 → "TV'de seçili metin alanı yok"
   - Bağlantı hatası → "TV'ye ulaşılamadı"
5. Başarılı gönderimden sonra metin kutusu temizlensin (bunu bir ayar yapma, şimdilik sabit olsun).
6. Telefon manifest izinleri: `INTERNET`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`.

TEST:
1. Telefon uygulamasını kur. TV bulunmalı (bulunamazsa elle IP).
2. TV'de bir metin alanına tıkla. Telefonda bir şey yaz ve GÖNDER'e bas → TV'de yazı çıkmalı.
3. Telefonda bir URL kopyala → "Panodan Yapıştır" → GÖNDER → TV'deki alana URL gelmeli.
4. TV'yi kapat, telefonda GÖNDER'e bas → "TV'ye ulaşılamadı" mesajı çıkmalı, uygulama çökmemeli.
5. TV'nin IP'sini router'dan değiştir (veya TV'yi yeniden başlat), telefon uygulamasını yeniden aç → TV tekrar bulunmalı.

---

## AŞAMA 4: Paylaş menüsü ve iyileştirmeler

Yapılacaklar:
1. Telefon uygulamasına `ACTION_SEND` + `text/plain` intent filter ekle. Başka bir uygulamada (tarayıcı, notlar) metin veya link seçip "Paylaş → TVPaste" dediğinde uygulama açılsın ve metin kutusuna otomatik gelsin. Gönder butonuna basmayı beklesin (otomatik gönderme YOK).
2. Telefon uygulamasında "Enter gönder" butonu: TV'ye özel `POST /key` isteği ile Enter tuşu için `GET`/`POST /action?name=enter` mantığı ekle. TV tarafında odaklı düğümde `ACTION_IME_ENTER` (API 30+) çağır. Çalışmazsa bu özelliği sessizce atla, uygulamayı bozma.
3. Telefon uygulamasında "Sil (backspace)" butonu: TV tarafında odaklı alandaki metnin son karakterini silsin (`/action?name=backspace`).
4. Uygulama ikonu ve adı: "TVPaste". Basit, tek renkli ikon yeterli.
5. README.md oluştur: kurulum, ADB komutları, sorun giderme (servis açılmıyor, keşif çalışmıyor, TV uygulamayı arka planda kapatıyor).

TEST:
1. Telefonda tarayıcıda bir sayfanın adresini "Paylaş → TVPaste" ile gönder.
2. Enter ve Sil butonları TV'de çalışmalı. Çalışmazsa uygulama hata vermemeli.

---

## BİLİNEN RİSKLER (OpenCode bunları bilsin)

- Android TV tarayıcılarında web sayfasındaki yazı alanları erişilebilirlik ağacında bazen görünmez. `TextInjector` boş dönerse logcat'e düğüm ağacını (sadece sınıf adı, `isEditable`, `isFocused`) yazdıran bir hata ayıklama (debug) fonksiyonu ekle ve bana göster.
- TCL TV arka planda uygulamaları kapatabilir. Erişilebilirlik servisi sistem tarafından yönetildiği için genelde yaşar, ama sorun olursa pil / otomatik başlatma ayarlarını kontrol etmem gerekecek.
- Android 14'te APK'yı dosya yöneticisiyle kurarsam erişilebilirlik ayarı "kısıtlı ayar" diye kilitlenebilir. ADB ile kurmak bunu önler.
- Her zaman önce bana "şu aşamayı bitirdim, test listesi şu" de. Ben test sonucunu yazmadan devam etme.
