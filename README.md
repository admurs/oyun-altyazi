# Oyun Altyazı

Konsol oyunlarındaki İngilizce altyazıları okuyup Türkçeye çeviren ve **TV'de oyun görüntüsünün üstünde** gösteren uygulama.

```
Xbox ──HDMI──▶ Google TV  (oyun gecikmesiz, TV'nin kendi girişinden akar)
  │                ▲
  │ Remote Play    │ çeviri (yerel ağ, HTTP)
  ▼                │
Tablet: Xbox uygulaması + Oyun Altyazı ─ ekranı oku → OCR → çevir ─┘
```

- **Tablet uygulaması** (`phone/`): Ekran kaydı izniyle Xbox uygulamasının Remote Play görüntüsünü okur, seçilen altyazı bölgesini kırpar, Google ML Kit ile yazıyı tanır, cihazda Türkçeye çevirir ve TV'ye gönderir.
- **TV uygulaması** (`tv/`): Erişilebilirlik servisi olarak çalışır ve çeviriyi HDMI girişi dahil her ekranın üstüne çizer. Ekrandaki içeriği okumaz.
- Görüntü hiçbir yere kaydedilmez veya gönderilmez. Yazı tanıma ve çeviri cihazda çalışır.

Planlanan: PS5 (Chiaki tabanlı alıcı), Xbox için yerleşik alıcı (Xbox uygulaması açık kalmadan), oyunlara göre bölge ayarları, isteğe bağlı yapay zekâ çevirisi.

## APK'lar

Her `main` push'unda GitHub Actions testleri koşar ve iki APK'yı **Releases** sayfasına koyar:

- `OyunAltyazi-Tablet.apk` → tablete
- `OyunAltyazi-TV.apk` → Google TV'ye

Tüm sürümler aynı anahtarla imzalanır; yenisi eskisinin üstüne kurulur.

## Kurulum

### Tablet
1. Releases'ten `OyunAltyazi-Tablet.apk`'yı indir ve aç. "Bilinmeyen uygulamalar" izni istenirse tarayıcıya izin ver.

### TV
TV'ye APK göndermenin iki yolu:
- **Send Files to TV** (önerilen): Uygulamayı hem tablete hem TV'ye Play Store'dan kur. Tabletten `OyunAltyazi-TV.apk`'yı TV'ye gönder, TV'de dosyayı açıp kur.
- **Downloader** (depo herkese açıksa): TV'ye Downloader uygulamasını kur, APK'nın Releases adresini gir.

TV ilk kez bilinmeyen kaynak isterse: *Ayarlar → Sistem/Gizlilik → Güvenlik ve kısıtlamalar → Bilinmeyen kaynaklar* altında kurulumu yapan uygulamaya izin ver.

## Test adımları

| # | Adım | Beklenen sonuç |
|---|------|----------------|
| 1 | TV'de **Oyun Altyazı TV**'yi aç → "1) Erişilebilirlik ayarlarını aç" → listeden **Oyun Altyazı katmanı**'nı aç | Uygulama ekranında "Katman servisi: AÇIK ✔" ve TV adresi görünür |
| 2 | "2) Burada test altyazısı göster" | Ekranın altında 5 sn beyaz yazı görünür |
| 3 | "3) HDMI testi" → 30 sn içinde Xbox'ın HDMI girişine geç | Yazı oyun görüntüsünün üstünde görünür. **Görünmüyorsa burada dur ve bildir.** |
| 4 | Tablette **Oyun Altyazı** → "TV'yi ağda ara" → çıkan TV'ye dokun | "bağlantı TAMAM ✔". Bulunamazsa TV ekranındaki adresi elle yaz → "Adresi kaydet" |
| 5 | "TV'ye gönder" | Yazdığın metin TV'de görünür, ~8 sn sonra kaybolur |
| 6 | "Modeli şimdi indir" (Wi-Fi) | "Model hazır ✔" ve örnek Türkçe cümle |
| 7 | "Başlat" → izin ekranında **Tüm ekran** veya **Xbox** → Xbox uygulamasına geç, Remote Play'de İngilizce altyazılı bir sahne aç | Bildirim çubuğunda "Oyun Altyazı çalışıyor" |
| 8 | Altyazı çıktıktan sonra Oyun Altyazı'ya dön → "Önizlemeyi yenile" | Önizlemede altyazı bölgesi, altında okunan İngilizce ve Türkçe çeviri görünür. Altyazı kutunun dışındaysa bölge kaydırıcılarını ayarla |
| 9 | Xbox uygulamasına dönüp oynamaya devam et | Altyazılar ~1 sn içinde TV'de Türkçe görünür, altyazı kaybolunca TV'den de kalkar |
| 10 | Bildirimdeki **Durdur** | Yakalama durur, TV'deki altyazı silinir |

Sorun bildirirken: hangi adım, tablet ekranındaki durum satırı ve TV ekranındaki "Hata:" satırı.

## Geliştirme

```
gradle testDebugUnitTest assembleDebug   # Gradle 8.11+, JDK 17, Android SDK 35
```

`phone/src/main/java/.../core/` Android'den bağımsız mantık (altyazı stabilizatörü), birim testleriyle birlikte.
