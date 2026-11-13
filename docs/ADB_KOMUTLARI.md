# TVPaste — ADB Komutları

Bu dosyadaki komutlar Windows bilgisayarda, proje klasöründe çalıştırılır.
TV'nin IP adresini TV ekranından (Aşama 2'den sonra) veya router'ın cihaz
listesinden öğrenebilirsin.

## 1. Kablosuz ADB bağlantısı

```powershell
adb connect TV_IP:5555
adb devices
```

`adb devices` listesinde TV görünmelidir. İlk bağlantıda TV ekranında
"Bu bilgisayara izin ver" uyarısı çıkarsa onayla.

## 2. APK'yı TV'ye kurma

Önce projeyi derle:

```powershell
gradlew.bat assembleDebug
```

Sonra kur:

```powershell
adb install -r tv\build\outputs\apk\debug\tv-debug.apk
```

## 3. Erişilebilirlik servisini komutla açma

```powershell
adb shell settings put secure enabled_accessibility_services com.tvpaste.tv/com.tvpaste.tv.PasteService
adb shell settings put secure accessibility_enabled 1
```

Servisi kapatmak için:

```powershell
adb shell settings put secure enabled_accessibility_services ""
adb shell settings put secure accessibility_enabled 0
```

> **ÖNEMLİ:** APK'yı her yeniden kurduktan sonra (`adb install -r`) mutlaka
> aşağıdaki iki satırı TEKRAR çalıştır. Aksi halde servis ayarlarda "açık"
> görünür ama sistem onu bağlamaz ve hiçbir şey yazmaz.
>
> ```powershell
> adb shell settings put secure enabled_accessibility_services ""
> adb shell settings put secure accessibility_enabled 0
> adb shell settings put secure enabled_accessibility_services com.tvpaste.tv/com.tvpaste.tv.PasteService
> adb shell settings put secure accessibility_enabled 1
> ```

Servisin gerçekten bağlandığını kontrol etmek için:

```powershell
adb shell dumpsys accessibility | Select-String "Bound services"
```

`Bound services:{}` boşsa servis çalışmıyor demektir; yukarıdaki aç/kapa
komutlarını uygula. Dolu ve içinde `label=TVPaste` yazıyorsa servis çalışıyor.

## 3b. TCL TV: "forbid bind service" hatası (AUTO_START)

TCL TV'ler arka planda servis bağlanmasını engelleyebilir. Logcat'te şunu
görürsen:

```
W ActivityManager: [TclAppBoot]:forbid bind service ComponentInfo{com.tvpaste.tv/...}
reason = 'callee_does't_have_OP_AUTO_START_permission.'
```

şu komutla uygulamaya otomatik başlatma izni ver:

```powershell
adb shell appops set com.tvpaste.tv AUTO_START allow
```

Sonra servisi kapat-aç (yukarıdaki 4 komut). Bu izin bazen APK yeniden
kurulunca sıfırlanır; servis bağlanmazsa ilk bunu kontrol et.

## 3c. Android 13+ kısıtlı ayarlar

Android 13 ve üstünde USB'den kurulan uygulamalar "kısıtlı" sayılır.
Erişilebilirlik anahtarı bu yüzden açılmaz. Şu izni ver:

```powershell
adb shell appops set com.tvpaste.tv ACCESS_RESTRICTED_SETTINGS allow
```

TCL TV'nin uygulama bilgisi ekranında bu izni açan menü yoktur.
Play Protect de erişilebilirlik bildiren APK'yı USB'den kurmaz.
Bu yüzden Android 13+ TV'lerde ADB veya Play Store gerekir.

## 4. Log izleme

```powershell
adb logcat -s PasteService
```

Servis bağlanınca şu satırı görmelisin:

```
PasteService baglandi
```

## 5. Uygulamayı uzaktan açma

```powershell
adb shell am start -n com.tvpaste.tv/.MainActivity
```

Web arayüzü: TV ile aynı ağdaki bir cihazın tarayıcısından `http://TV_IP:8080`
adresi açılır (ör. `http://192.168.1.38:8080`).
Sayfada açık/koyu tema düğmesi vardır.

## 6. Telefon uygulamasını kurma (Aşama 3)

```powershell
adb install -r phone\build\outputs\apk\debug\phone-debug.apk
```

## 7. Sorun giderme

### Servis bağlanmıyor (ekranda "Servis çalışmıyor" yazıyor)

**Önce şu komuta bak** — TCL TV'ler servisin arka planda bağlanmasını
engelleyebilir:

```powershell
adb shell appops set com.tvpaste.tv AUTO_START allow
```

Sonra servisi kapat-aç (3. bölümdeki 4 komut) ve kontrol et:

```powershell
adb shell dumpsys accessibility | Select-String "Bound services"
```

`Bound services` içinde `label=TVPaste` varsa servis çalışıyor demektir.
Yoksa logcat'te `forbid bind service` satırı olup olmadığına bak.

### Ekranda "Sunucu: ÇALIŞMIYOR" yazıyor

1. Erişilebilirlik servisi açık mı? (üstteki durum yazısı "AÇIK" olmalı)
2. Logcat'te `Sunucu basladi: port 8080` satırı var mı?
3. `Sunucu baslatilamadi` yazıyorsa port 8080 başka bir uygulama tarafından
   kullanılıyor olabilir. TV'yi yeniden başlat.

### Telefon TV'yi bulamıyor (Aşama 3)

1. Telefon ve TV **aynı Wi-Fi ağında** mı?
2. TV ekranındaki IP'yi telefonda "Elle IP gir" alanına yaz (ör. `192.168.1.38`).
3. Bazı router'lar cihazlar arası trafiği engeller ("AP isolation" /
   "istemci izolasyonu" ayarı kapalı olmalı).
4. TV'deki güvenlik duvarı / misafir ağı kullanılıyorsa kapat.

### "Uygulama engellendi" (Play Protect)

Android 13+ USB kurulumunda Play Protect engeller.
ADB ile kur veya Play Store kullan. Kurucu APK bu engeli aşmaz.

### Erişilebilirlik anahtarı açılmıyor

`ACCESS_RESTRICTED_SETTINGS` kapalıdır. Şu komutu çalıştır:

```powershell
adb shell appops set com.tvpaste.tv ACCESS_RESTRICTED_SETTINGS allow
```

TCL TV'de "kısıtlı ayarlara izin ver" menüsü yoktur.

### Metin yazılmıyor (HTTP 409 `no-focused-field`)

1. TV'de gerçekten bir metin alanına tıklanmış ve klavye açılmış olmalı.
2. Bazı TV tarayıcıları yazı alanını erişilebilirlik ağacında göstermez.
   Logcat'teki `--- agac baslangici ---` dökümüne bak (sadece sınıf adı,
   `editable`, `focused` yazar).
3. Farklı bir uygulama ile dene (tarayıcı yerine arama kutusu gibi).

## 8. Play Store (gelecek)

Play Console hesabı tek seferlik 25 ABD dolarıdır. Yerel vergi eklenebilir.
Play sürümü release imza, ikon ve gizlilik politikası ister.
Play, erişilebilirlik kullanımı için beyan ve uygulama içi onay ister.
Bu yol henüz kurulmadı.

