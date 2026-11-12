# CyayoGathering

Plugin modular untuk sistem gathering fleksibel (Mining, Woodcutting, Farming, Fishing, dsb.) di Spigot/Paper 1.21.1 dengan integrasi PacketEvents, FancyHolograms, MMOItems, dan CyayoLuckRNG.

---

## 🌟 Fitur Utama

1. **Invisible Interaction Hitbox**:
   - Menggunakan entitas native Minecraft `Interaction` (`org.bukkit.entity.Interaction`).
   - Node tidak memerlukan blok fisik dan bisa ditempatkan di mana saja (daratan, melayang di atas air untuk fishing spot, dsb.).
   - Dilengkapi sistem stempel **PDC (PersistentDataContainer)** untuk membersihkan entitas nyangkut secara otomatis (*auto-prune*).
2. **FancyHolograms Real-Time Display**:
   - Menggunakan API resmi FancyHolograms (`forceUpdate()`).
   - Progress bar berjalan instan dan mulus tanpa kedip (*flicker*).
   - Mode non-persistent (`data.setPersistent(false)`) untuk mencegah penumpukan data di database FancyHolograms.
   - Dilengkapi command pembersih darurat: `/cgath purgeholograms`.
3. **Instanced Virtual Drops (PacketEvents 2.7.0)**:
   - Drop item di tanah hanya terlihat dan hanya dapat diambil oleh player pemiliknya.
   - Player mengambil item secara alami dengan berjalan mendekatinya (*manual walk-over*).
   - Dilengkapi proteksi jika inventori penuh.
4. **Sistem Gathering Luck Mandiri (Infinite Scaling Multiplier)**:
   - Format drop table berbasis undian 1/Base RNG yang terukur.
   - **Self-Handled Multiplier**: Menggunakan sistem pengali peluang ala MMORPG (`Effective Base = Base / Multiplier`).
   - Mendukung penimbunan stat Luck hingga ratusan atau ribuan di fase endgame tanpa merusak drop rate atau menyebabkan 100% instan drop.
5. **MMOItems Custom Stats**:
   - `GATHERING_SPEED`: Mempercepat durasi channeling.
   - `GATHERING_DOUBLE_DROP`: Peluang mendapatkan 2x drop sekaligus.
   - `GATHERING_LUCK`: Meningkatkan peluang mendapatkan material langka dari gathering nodes.
6. **Integrasi CyayoMilestone & Jobs**:
   - Mencatat total gathering per kategori (`mining`, `fishing`, dll) dan per node.
   - PlaceholderAPI siap pakai: `%cyayogathering_gathered_category_mining%`.
   - Custom event: `NodeGatherCompleteEvent`.

---

## 🛠️ Command & Permission

> **Keamanan**: Seluruh perintah admin hanya dapat digunakan oleh Console atau Player dengan izin `cyayogathering.admin` (default OP). Player biasa tidak akan melihat saran perintah pada tab-completer.

| Command | Deskripsi |
| :--- | :--- |
| `/cgath create <tipe> [id]` | Membuat node baru di lokasi player berdiri. |
| `/cgath remove [id]` | Menghapus node (jika tanpa ID, otomatis menghapus node terdekat dalam 3.5 blok). |
| `/cgath wand` | Memberikan tongkat admin Gathering Wand (Klik kiri info, Shift+Klik kiri hapus). |
| `/cgath near [radius]` | Melihat daftar node terdekat di sekitar pemain beserta jaraknya. |
| `/cgath tp <id>` | Teleportasi langsung ke lokasi node. |
| `/cgath reset <id>` | Mereset sisa charge node ke penuh dan membatalkan cooldown. |
| `/cgath list` | Menampilkan seluruh node yang aktif di server. |
| `/cgath purgeholograms` | Membersihkan seluruh FancyHologram nyangkut berprefix `cyayogath_node_`. |
| `/cgath reload` | Memuat ulang seluruh konfigurasi, template `nodes/*.yml`, dan refresh visual. |

---

## 📊 PlaceholderAPI

- `%cyayogathering_is_gathering%`: `true` / `false`
- `%cyayogathering_speed%`: Nilai Gathering Speed player (contoh: `+20.0%`)
- `%cyayogathering_gathering_luck%` / `%cyayogathering_luck%`: Nilai stat Gathering Luck (contoh: `150` atau `25.5`)
- `%cyayogathering_gathering_luck_formatted%`: Nilai stat dengan tanda plus (contoh: `+150` atau `+25.5`)
- `%cyayogathering_gathering_luck_raw%`: Nilai angka mentah stat (contoh: `150.0`)
- `%cyayogathering_gathering_luck_multiplier%`: Total pengali peluang drop (contoh: `2.50x`)
- `%cyayogathering_total_nodes%`: Total node aktif di server
- `%cyayogathering_gathered_total%`: Total gathering yang pernah dilakukan player
- `%cyayogathering_gathered_category_<category>%`: Total gathering per kategori (contoh: `%cyayogathering_gathered_category_mining%`)
- `%cyayogathering_gathered_node_<node_type>%`: Total gathering pada node spesifik
- `%cyayogathering_node_status_<node_id>%`: Status node (`READY`, `GATHERING`, `COOLDOWN`)
- `%cyayogathering_node_charges_<node_id>%`: Sisa charge pada node tersebut

---

## 💻 Developer API (`CyayoGatheringAPI`)

```java
// Cek status player
boolean isGathering = CyayoGatheringAPI.isGathering(player);

// Dapatkan statistik player untuk Jobs / Milestone
int miningGathers = CyayoGatheringAPI.getPlayerCategoryGathers(player.getUniqueId(), "mining");

// Buat node via kode
ActiveNode node = CyayoGatheringAPI.createNode("mining_agate", location);
```

### Custom Events:
- `NodeGatherStartEvent` *(Cancellable)*
- `NodeGatherTickEvent`
- `NodeGatherCompleteEvent`
- `NodeGatherCancelEvent`
- `NodeDepletedEvent`
- `NodeRespawnEvent`
