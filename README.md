# 🐱 Koceng Oren AI

**Peliharaan Kucing 3D Interaktif yang Ketus, Nyolot, tapi Super Pintar!**

**Koceng Oren AI** adalah aplikasi *virtual pet* 3D berbasis Android yang menggabungkan karakter kucing oren kartun interaktif dengan kecerdasan buatan **Google Gemini**. Berbeda dari asisten AI yang kaku dan terlalu sopan, Koceng Oren punya kepribadian khas kucing oren bar-bar: blak-blakan, ketus, suka menyindir, tapi tetap jago menjawab pertanyaan dan membantu mengerjakan **PR sekolah** langkah demi langkah!

## ✨ Fitur Utama

- **🎙️ Obrolan Suara Alami (Gemini Natural Voice):** Ajak Koceng Oren ngobrol layaknya manusia sungguhan lewat mikrofon atau ketik langsung. Balasannya diucapkan dengan suara alami Gemini, bukan suara robot TTS biasa.
- **😈 Kepribadian Kucing Oren Bar-bar:** Bisa diatur ke 3 level sifat (*Agak Cuek*, *Ketus Standar*, hingga *Super Bar-Bar*) yang bikin obrolan selalu hidup dan menghibur.
- **📚 Mode PR Sekolah & 📷 Foto Soal PR (Gemini Vision):** Aktifkan mode **PR Sekolah** untuk menanyakan soal Matematika, IPA, IPS, Bahasa Inggris, hingga Sejarah. Cukup ketik soalnya atau **foto langsung dari buku/galeri**, dan Koceng Oren akan menjabarkan cara pengerjaannya secara runtut lengkap dengan jawaban akhir.
- **📋 Salin Jawaban & Riwayat Obrolan:** Salin jawaban PR dalam 1 ketukan lewat tombol **Salin**, atau buka kembali seluruh percakapan di jendela **Riwayat**.
- **🎨 UI Kartun Modern & Fullscreen:** Tampilan *Playful Cartoon UI* yang tajam dengan dukungan layar penuh (*edge-to-edge*), jarak aman kamera depan, serta *Splash Screen* beranimasi.
- **🐾 Karakter 3D Interaktif & Efek Ekspresi:** Sentuh kepala atau perut Koceng Oren untuk memicu reaksi suara ketus dan efek partikel kartun (tanda tanya saat berpikir, lampu ide saat menjawab PR, hingga simbol kesal saat dicolek).
- **⚙️ Kustomisasi Suara & Tema Ruangan:** Pilih berbagai karakter suara Gemini (`Puck`, `Fenrir`, `Charon`, `Aoede`, `Kore`) dan ubah suasana ruangan (*Siang Cerah*, *Malam Lampu Tidur*, atau *Ruang Belajar Hangat*).

## 🛠️ Teknologi yang Digunakan

- **Bahasa & Engine:** Kotlin, LibGDX, & `gdx-gltf` (Rendering 3D PBR)
- **Antarmuka:** Native Android Cartoon HUD Overlay
- **Kecerdasan Buatan:** Google Gemini API (`gemini-3.5-flash-lite` untuk chat & Vision, `gemini-3.8-flash-tts` / `gemini-3.8-flash-lite-tts` untuk suara alami)
- **Otomatisasi Build:** GitHub Actions CI/CD (otomatis membangun `koceng-oren-debug-apk`)

## 🚀 Cara Build APK (GitHub Actions)

1. Fork atau clone repositori ini.
2. Tambahkan API Key Google Gemini Anda di **Settings → Secrets and variables → Actions** dengan nama **`GEMINI_API_KEY`**.
3. Push perubahan ke branch `main` atau jalankan workflow di tab **Actions**.
4. Unduh artefak **`koceng-oren-debug-apk`** setelah build selesai, lalu instal di perangkat Android Anda.
