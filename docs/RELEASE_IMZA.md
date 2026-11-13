# TVPaste — Release İmzası

Bu dosya release APK ve AAB imzalamayı anlatır.

## Dosyalar

| Dosya | Yer | Git'e girer mi? |
|---|---|---|
| Release keystore | `keystore/tvpaste-release.jks` | Hayır |
| Şifreler | `keystore.properties` | Hayır |
| İmzalama ayarı | `tv/build.gradle.kts`, `phone/build.gradle.kts` | Evet |

`keystore.properties` ve `keystore/` `.gitignore` içindedir.
Bu iki dosyayı asla GitHub'a yükleme.

## Keystore bilgileri

- Alias: `tvpaste`
- Tür: PKCS12
- Anahtar: RSA 4096 bit
- Geçerlilik: 10000 gün
- Sertifika: `CN=TVPaste, OU=Development, O=TVPaste, L=Istanbul, ST=Istanbul, C=TR`
- SHA-256: `5C:9F:50:DB:9C:CB:3B:F2:95:A7:B5:BD:88:89:C0:B2:D0:60:91:BA:43:13:E4:2F:E5:2E:4E:4A:3E:D8:2B:37`

## keystore.properties

Dosya proje kökünde durur. İçeriği şöyle olmalı:

```
storeFile=keystore/tvpaste-release.jks
storePassword=...
keyAlias=tvpaste
keyPassword=...
```

Şifreler bu dosyada yazılıdır. Dosyayı kimseyle paylaşma.

## Derleme

Release APK:

```powershell
gradlew.bat assembleRelease
```

Çıktı: `tv\build\outputs\apk\release\tv-release.apk`
Telefon: `phone\build\outputs\apk\release\phone-release.apk`

Play Store için AAB:

```powershell
gradlew.bat bundleRelease
```

Çıktı: `tv\build\outputs\bundle\release\tv-release.aab`
Telefon: `phone\build\outputs\bundle\release\phone-release.aab`

## İmza kontrolü

```powershell
C:\Android\Sdk\build-tools\34.0.0\apksigner.bat verify --print-certs tv\build\outputs\apk\release\tv-release.apk
```

Signer DN ve SHA-256 yukarıdakiyle aynı olmalı.

## Play App Signing

Play Store'a yüklerken bu keystore **upload key** olur.
Google, uygulamanın gerçek imza anahtarını kendi saklar.
Buna Play App Signing denir. Yeni uygulamalarda varsayılandır.

Upload key kaybolursa Google'dan upload key sıfırlama isteyebilirsin.
Play App Signing yoksa anahtar kaybı güncellemeyi engeller.

## Yedekleme

Şunları iki güvenli yerde sakla:

- `keystore\tvpaste-release.jks`
- `keystore.properties` (şifreler)

Öneri: şifre yöneticisi + şifreli USB bellek.
Keystore'u kaybetmek, aynı imzayla güncelleme yapamaman demektir.

## Yeni keystore oluşturma

Gerekirse:

```powershell
keytool -genkeypair -v -keystore keystore\tvpaste-release.jks -storetype PKCS12 -alias tvpaste -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=TVPaste, OU=Development, O=TVPaste, L=Istanbul, ST=Istanbul, C=TR"
```

Komut şifre sorar. Şifreyi `keystore.properties` dosyasına yaz.

## Sürüm artırma

Her Play yüklemesinde `versionCode` artmalı.
`versionCode` ve `versionName` iki modülde de güncellenir.
Şu an: `versionCode = 1`, `versionName = "1.0"`.

## Debug ve release farkı

Debug APK yerel test içindir; debug anahtarıyla imzalanır.
Release APK/AAB yukarıdaki keystore ile imzalanır.
Debug APK'nın üzerine release APK kurulmaz; imza uyuşmaz.
Önce debug sürümü kaldırılmalıdır.
