import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'models.dart';
import 'store.dart';
import 'ui.dart';

// ======================= SIM =======================
class SimTab extends StatelessWidget {
  const SimTab({super.key});

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final list = [...store.sims]..sort((a, b) => a.expiry.compareTo(b.expiry));
    return SafeArea(
      child: ListView(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 96),
        children: [
          Row(children: [
            const Expanded(child: Text('SIM Saya', style: TextStyle(fontSize: 24, fontWeight: FontWeight.w800))),
            FilledButton.icon(
              onPressed: () => showDialog(context: context, builder: (_) => const SimDialog()),
              icon: const Icon(Icons.add_rounded),
              label: const Text('Tambah SIM'),
            ),
          ]),
          const SizedBox(height: 14),
          if (list.isEmpty)
            Panel(
              child: Column(children: [
                Icon(Icons.badge_rounded, size: 48, color: cs.onSurfaceVariant),
                const SizedBox(height: 8),
                const Text('Belum ada data SIM', style: TextStyle(fontWeight: FontWeight.w700)),
                Text('Catat masa berlaku SIM agar diingatkan sebelum habis.',
                    textAlign: TextAlign.center, style: TextStyle(color: cs.onSurfaceVariant, fontSize: 12)),
              ]),
            ),
          for (final s in list)
            Panel(
              margin: const EdgeInsets.only(bottom: 12),
              onTap: () => showDialog(context: context, builder: (_) => SimDialog(edit: s)),
              child: Row(children: [
                Container(
                  width: 48,
                  height: 48,
                  decoration: BoxDecoration(color: cs.primaryContainer, borderRadius: BorderRadius.circular(14)),
                  child: Icon(Icons.badge_rounded, color: cs.primary),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text('${s.type}${s.name.isEmpty ? '' : ' · ${s.name}'}', style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16)),
                    if (s.number.isNotEmpty) Text(s.number, style: TextStyle(color: cs.onSurfaceVariant, fontSize: 12)),
                    Text('Berlaku s/d ${fmtDate(s.expiry)}', style: TextStyle(color: cs.onSurfaceVariant, fontSize: 12)),
                  ]),
                ),
                Pill(
                  s.daysLeft < 0 ? 'Habis ${-s.daysLeft} hari' : '${s.daysLeft} hari lagi',
                  levelColor(s.daysLeft, store.simWarn),
                ),
              ]),
            ),
        ],
      ),
    );
  }
}

class SimDialog extends StatefulWidget {
  final SimItem? edit;
  const SimDialog({super.key, this.edit});
  @override
  State<SimDialog> createState() => _SimDialogState();
}

class _SimDialogState extends State<SimDialog> {
  static const types = ['SIM A', 'SIM B I', 'SIM B II', 'SIM C', 'SIM D'];
  final _name = TextEditingController();
  final _no = TextEditingController();
  String type = 'SIM C';
  DateTime? expiry;

  @override
  void initState() {
    super.initState();
    final e = widget.edit;
    if (e != null) {
      _name.text = e.name;
      _no.text = e.number;
      type = types.contains(e.type) ? e.type : 'SIM C';
      expiry = e.expiry;
    }
  }

  @override
  void dispose() {
    _name.dispose();
    _no.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(widget.edit == null ? 'Tambah SIM' : 'Ubah SIM'),
      content: SingleChildScrollView(
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          DropdownButtonFormField<String>(
            value: type,
            decoration: const InputDecoration(labelText: 'Jenis SIM', border: OutlineInputBorder()),
            items: types.map((t) => DropdownMenuItem(value: t, child: Text(t))).toList(),
            onChanged: (v) => setState(() => type = v ?? type),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _name,
            textCapitalization: TextCapitalization.words,
            decoration: const InputDecoration(labelText: 'Nama pemilik', border: OutlineInputBorder()),
          ),
          const SizedBox(height: 12),
          TextField(controller: _no, decoration: const InputDecoration(labelText: 'Nomor SIM (opsional)', border: OutlineInputBorder())),
          const SizedBox(height: 12),
          OutlinedButton.icon(
            style: OutlinedButton.styleFrom(minimumSize: const Size.fromHeight(50), alignment: Alignment.centerLeft),
            onPressed: () async {
              final d = await pickDate(context, expiry);
              if (d != null) setState(() => expiry = d);
            },
            icon: const Icon(Icons.event_rounded),
            label: Text(expiry == null ? 'Masa berlaku s/d' : fmtDate(expiry!)),
          ),
        ]),
      ),
      actions: [
        if (widget.edit != null)
          TextButton(
            onPressed: () async {
              await store.deleteSim(widget.edit!.id);
              if (context.mounted) Navigator.pop(context);
            },
            child: const Text('Hapus'),
          ),
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('Batal')),
        FilledButton(
          onPressed: () async {
            if (expiry == null) {
              snack(context, 'Pilih masa berlaku SIM');
              return;
            }
            await store.saveSim(SimItem(
              id: widget.edit?.id ?? newId(),
              type: type,
              name: _name.text.trim(),
              number: _no.text.trim(),
              expiry: expiry!,
            ));
            if (context.mounted) Navigator.pop(context);
          },
          child: const Text('Simpan'),
        ),
      ],
    );
  }
}

// ======================= PENGATURAN =======================
class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  Future<void> _editText(BuildContext context, String title, String initial, Future<void> Function(String) onSave) async {
    final c = TextEditingController(text: initial);
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(title),
        content: TextField(controller: c, autofocus: true, decoration: const InputDecoration(border: OutlineInputBorder())),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Batal')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Simpan')),
        ],
      ),
    );
    if (ok == true && c.text.trim().isNotEmpty) await onSave(c.text.trim());
  }

  Future<void> _editInt(BuildContext context, String title, int value, String suffix, Future<void> Function(int) onSave) async {
    final c = TextEditingController(text: '$value');
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(title),
        content: TextField(
          controller: c,
          autofocus: true,
          keyboardType: TextInputType.number,
          decoration: InputDecoration(suffixText: suffix, border: const OutlineInputBorder()),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Batal')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Simpan')),
        ],
      ),
    );
    if (ok == true) await onSave(parseInt(c.text));
  }

  Widget _head(BuildContext context, String t) => Padding(
        padding: const EdgeInsets.fromLTRB(16, 20, 16, 4),
        child: Text(t, style: TextStyle(fontWeight: FontWeight.w800, color: Theme.of(context).colorScheme.primary)),
      );

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: store,
      builder: (context, _) {
        const themes = ['Otomatis (sesuai jam)', 'Terang', 'Senja', 'Gelap'];
        return Scaffold(
          appBar: AppBar(title: const Text('Pengaturan')),
          body: ListView(children: [
            _head(context, 'Profil'),
            ListTile(
              leading: const Icon(Icons.person_rounded),
              title: const Text('Nama'),
              subtitle: Text(store.userName ?? '-'),
              onTap: () => _editText(context, 'Nama Anda', store.userName ?? '', store.setUser),
            ),
            _head(context, 'Tema'),
            for (var i = 0; i < themes.length; i++)
              ListTile(
                leading: Icon(store.themeMode == i ? Icons.radio_button_checked_rounded : Icons.radio_button_off_rounded),
                title: Text(themes[i]),
                subtitle: i == 0 ? const Text('05.00–14.59 terang · 15.00–17.59 senja · 18.00–04.59 gelap') : null,
                onTap: () => store.setSettings(theme: i),
              ),
            _head(context, 'Notifikasi'),
            SwitchListTile(
              secondary: const Icon(Icons.notifications_active_rounded),
              title: const Text('Pengingat servis, oli, pajak & SIM'),
              value: store.notifEnabled,
              onChanged: (v) {
                store.setSettings(notif: v);
                if (v) Native.requestNotif();
              },
            ),
            ListTile(
              leading: const Icon(Icons.opacity_rounded),
              title: const Text('Ingatkan ganti oli saat sisa'),
              trailing: Text('${fmtNum(store.oilWarn)} km'),
              onTap: () => _editInt(context, 'Sisa KM sebelum ganti oli', store.oilWarn, 'km', (v) => store.setSettings(oil: v)),
            ),
            ListTile(
              leading: const Icon(Icons.build_rounded),
              title: const Text('Ingatkan servis saat sisa'),
              trailing: Text('${fmtNum(store.serviceWarn)} km'),
              onTap: () => _editInt(context, 'Sisa KM sebelum servis', store.serviceWarn, 'km', (v) => store.setSettings(service: v)),
            ),
            ListTile(
              leading: const Icon(Icons.receipt_long_rounded),
              title: const Text('Ingatkan pajak'),
              trailing: Text('${store.taxWarn} hari sebelum'),
              onTap: () => _editInt(context, 'Hari sebelum jatuh tempo pajak', store.taxWarn, 'hari', (v) => store.setSettings(tax: v)),
            ),
            ListTile(
              leading: const Icon(Icons.badge_rounded),
              title: const Text('Ingatkan masa berlaku SIM'),
              trailing: Text('${store.simWarn} hari sebelum'),
              onTap: () => _editInt(context, 'Hari sebelum SIM habis', store.simWarn, 'hari', (v) => store.setSettings(sim: v)),
            ),
            ListTile(
              leading: const Icon(Icons.notification_add_rounded),
              title: const Text('Tes notifikasi sekarang'),
              onTap: () async {
                await Native.requestNotif();
                await Native.checkNow();
                if (context.mounted) snack(context, 'Jika ada item yang mendekati jadwal, notifikasi akan muncul.');
              },
            ),
            _head(context, 'Widget layar utama'),
            ListTile(
              leading: const Icon(Icons.widgets_rounded),
              title: const Text('Tambahkan widget GarasiKu'),
              subtitle: const Text('Pilih kendaraan yang tampil lewat opsi “Tampilkan di widget” pada tiap kendaraan. Geser widget untuk ganti kendaraan.'),
              onTap: () async {
                final ok = await Native.pinWidget();
                if (!ok && context.mounted) {
                  snack(context, 'Tekan lama layar utama → Widget → GarasiKu.');
                }
              },
            ),
            _head(context, 'Backup & Restore'),
            ListTile(
              leading: const Icon(Icons.backup_rounded),
              title: const Text('Backup data'),
              subtitle: const Text('Simpan semua data & foto kendaraan ke file (GarasiKu-tanggal).'),
              onTap: () async {
                final name = 'GarasiKu-${DateFormat('dd-MM-yyyy').format(DateTime.now())}.json';
                final content = await store.backupString();
                final ok = await Native.saveBackup(name, content);
                if (context.mounted) snack(context, ok ? 'Backup tersimpan: $name' : 'Backup dibatalkan');
              },
            ),
            ListTile(
              leading: const Icon(Icons.settings_backup_restore_rounded),
              title: const Text('Restore data'),
              subtitle: const Text('Pilih file backup GarasiKu. Data saat ini akan diganti.'),
              onTap: () async {
                final ok = await showDialog<bool>(
                  context: context,
                  builder: (ctx) => AlertDialog(
                    title: const Text('Restore data?'),
                    content: const Text('Data saat ini akan diganti dengan isi file backup.'),
                    actions: [
                      TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Batal')),
                      FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Pilih file')),
                    ],
                  ),
                );
                if (ok != true) return;
                final text = await Native.pickBackup();
                if (text == null) return;
                final done = await store.restoreFromString(text);
                if (context.mounted) snack(context, done ? 'Data berhasil di-restore' : 'File bukan backup GarasiKu yang valid');
              },
            ),
            const SizedBox(height: 32),
          ]),
        );
      },
    );
  }
}
