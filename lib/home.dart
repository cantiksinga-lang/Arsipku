import 'dart:io';
import 'package:flutter/material.dart';
import 'forms.dart';
import 'models.dart';
import 'more.dart';
import 'store.dart';
import 'ui.dart';

Widget vehiclePhoto(Vehicle v, double h) {
  final p = v.photo;
  if (p != null && File(p).existsSync()) {
    return Image.file(File(p), height: h, width: double.infinity, fit: BoxFit.cover, cacheWidth: 900);
  }
  return Container(
    height: h,
    width: double.infinity,
    decoration: const BoxDecoration(
      gradient: LinearGradient(colors: [Color(0xFF18A0E0), Color(0xFF0858A8)], begin: Alignment.topLeft, end: Alignment.bottomRight),
    ),
    child: Icon(v.type == 'Mobil' ? Icons.directions_car_rounded : Icons.two_wheeler_rounded, size: 72, color: Colors.white70),
  );
}

List<Widget> statusPills(Vehicle v) {
  final pills = <Widget>[];
  final td = v.taxDays;
  if (td != null) {
    pills.add(Pill(
      td < 0 ? 'Pajak lewat ${-td} hari' : (td == 0 ? 'Pajak jatuh tempo hari ini' : 'Pajak: $td hari lagi'),
      levelColor(td, store.taxWarn),
    ));
  }
  if (v.oilInterval > 0) {
    pills.add(Pill(
      v.oilLeft <= 0 ? 'Ganti oli sekarang!' : 'Oli mesin: ${fmtNum(v.oilLeft)} km lagi',
      levelColor(v.oilLeft, store.oilWarn),
    ));
  }
  if (v.serviceInterval > 0) {
    pills.add(Pill(
      v.serviceLeft <= 0 ? 'Waktunya servis!' : 'Servis: ${fmtNum(v.serviceLeft)} km lagi',
      levelColor(v.serviceLeft, store.serviceWarn),
    ));
  }
  return pills;
}

class HomeTab extends StatefulWidget {
  const HomeTab({super.key});
  @override
  State<HomeTab> createState() => _HomeTabState();
}

class _HomeTabState extends State<HomeTab> {
  bool yearOnly = true;
  bool compact = false;
  String query = '';

  Future<void> _search() async {
    final c = TextEditingController(text: query);
    final r = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Cari kendaraan'),
        content: TextField(
          controller: c,
          autofocus: true,
          decoration: const InputDecoration(hintText: 'Nama atau plat nomor', border: OutlineInputBorder()),
          onSubmitted: (v) => Navigator.pop(ctx, v),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, ''), child: const Text('Reset')),
          FilledButton(onPressed: () => Navigator.pop(ctx, c.text), child: const Text('Cari')),
        ],
      ),
    );
    if (r != null) setState(() => query = r.trim());
  }

  Widget _summary(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final year = DateTime.now().year;
    final list = store.txs.where((t) => !yearOnly || t.date.year == year).toList();
    final fuel = list.where((t) => t.cat.isFuel).toList();
    final other = list.where((t) => !t.cat.isFuel).toList();
    final fuelSum = fuel.fold<int>(0, (s, t) => s + t.amount);
    final otherSum = other.fold<int>(0, (s, t) => s + t.amount);
    final total = fuelSum + otherSum;
    final taxSum = store.vehicles.fold<int>(0, (s, v) => s + v.taxAmount);

    return Panel(
      margin: const EdgeInsets.only(bottom: 14),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text('Ringkasan Biaya Garasi', style: TextStyle(color: cs.onSurfaceVariant, fontWeight: FontWeight.w600)),
              const SizedBox(height: 4),
              Text(rp(total), style: const TextStyle(fontSize: 26, fontWeight: FontWeight.w800)),
              Text(yearOnly ? 'Tahun $year · ${list.length} transaksi' : 'Semua waktu · ${list.length} transaksi',
                  style: TextStyle(color: cs.onSurfaceVariant, fontSize: 12)),
            ]),
          ),
          SegmentedButton<bool>(
            showSelectedIcon: false,
            style: const ButtonStyle(visualDensity: VisualDensity.compact),
            segments: [
              ButtonSegment(value: true, label: Text('$year')),
              const ButtonSegment(value: false, label: Text('Semua')),
            ],
            selected: {yearOnly},
            onSelectionChanged: (s) => setState(() => yearOnly = s.first),
          ),
        ]),
        const SizedBox(height: 12),
        ClipRRect(
          borderRadius: BorderRadius.circular(99),
          child: SizedBox(
            height: 7,
            child: total == 0
                ? Container(color: cs.outlineVariant)
                : Row(children: [
                    if (otherSum > 0) Expanded(flex: otherSum, child: Container(color: cs.primary)),
                    if (fuelSum > 0) Expanded(flex: fuelSum, child: Container(color: Cat.bbm.color)),
                  ]),
          ),
        ),
        const SizedBox(height: 12),
        Row(children: [
          Expanded(child: _legend(cs.primary, 'Servis · ${other.length}×', rp(otherSum))),
          Container(width: 1, height: 44, color: cs.outlineVariant),
          const SizedBox(width: 14),
          Expanded(child: _legend(Cat.bbm.color, 'BBM · ${fuel.length}×', rp(fuelSum))),
        ]),
        Divider(height: 24, color: cs.outlineVariant),
        Row(children: [
          Expanded(
            child: Text('Estimasi pajak tahunan (${store.vehicles.length} kendaraan)',
                style: TextStyle(color: cs.onSurfaceVariant, fontSize: 12)),
          ),
          Text('${rp(taxSum)}/thn', style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 13)),
        ]),
      ]),
    );
  }

  Widget _legend(Color c, String label, String value) => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Container(width: 8, height: 8, decoration: BoxDecoration(color: c, shape: BoxShape.circle)),
          const SizedBox(width: 6),
          Text(label, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 12)),
        ]),
        const SizedBox(height: 4),
        Text(value, style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16)),
      ]);

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final now = DateTime.now();
    final q = query.toLowerCase();
    final vs = store.vehicles.where((v) => q.isEmpty || '${v.name} ${v.plate}'.toLowerCase().contains(q)).toList();

    return SafeArea(
      child: ListView(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 96),
        children: [
          Row(children: [
            Container(
              width: 52,
              height: 52,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(16),
                gradient: LinearGradient(colors: [cs.primary, cs.primary.withOpacity(0.7)], begin: Alignment.topLeft, end: Alignment.bottomRight),
              ),
              child: Icon(Icons.build_rounded, color: cs.onPrimary),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                const Text('GarasiKu', style: TextStyle(fontSize: 24, fontWeight: FontWeight.w800)),
                Text('Rawat Kendaraan & Status SIM', style: TextStyle(color: cs.onSurfaceVariant, fontSize: 12)),
              ]),
            ),
            IconButton.filledTonal(onPressed: () => store.commit(), icon: const Icon(Icons.sync_rounded)),
            const SizedBox(width: 6),
            IconButton.filledTonal(
              onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const SettingsScreen())),
              icon: const Icon(Icons.settings_rounded),
            ),
          ]),
          const SizedBox(height: 18),
          Text('${greetingFor(now)}, ${store.userName ?? ''}!',
              style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800)),
          const SizedBox(height: 14),
          Row(children: [
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                const Text('Kendaraan Saya', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
                Text(q.isEmpty ? '${vs.length} kendaraan' : '${vs.length} hasil untuk "$query"',
                    style: TextStyle(color: cs.onSurfaceVariant, fontSize: 12)),
              ]),
            ),
            IconButton.outlined(onPressed: _search, icon: Icon(query.isEmpty ? Icons.search_rounded : Icons.filter_alt_rounded)),
            const SizedBox(width: 6),
            IconButton.filledTonal(
              onPressed: () => setState(() => compact = !compact),
              icon: Icon(compact ? Icons.view_agenda_rounded : Icons.view_list_rounded),
            ),
            const SizedBox(width: 6),
            IconButton.filled(
              onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const VehicleForm())),
              icon: const Icon(Icons.add_rounded),
            ),
          ]),
          const SizedBox(height: 12),
          _summary(context),
          if (store.vehicles.isEmpty)
            Panel(
              child: Column(children: [
                Icon(Icons.two_wheeler_rounded, size: 48, color: cs.onSurfaceVariant),
                const SizedBox(height: 8),
                const Text('Belum ada kendaraan', style: TextStyle(fontWeight: FontWeight.w700)),
                const SizedBox(height: 10),
                FilledButton.icon(
                  onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const VehicleForm())),
                  icon: const Icon(Icons.add_rounded),
                  label: const Text('Tambah kendaraan'),
                ),
              ]),
            ),
          for (final v in vs) VehicleCard(v, compact: compact),
        ],
      ),
    );
  }
}

class VehicleCard extends StatelessWidget {
  final Vehicle v;
  final bool compact;
  const VehicleCard(this.v, {super.key, this.compact = false});

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Panel(
      padding: EdgeInsets.zero,
      margin: const EdgeInsets.only(bottom: 14),
      onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => VehicleDetail(id: v.id))),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        if (!compact)
          Stack(children: [
            ClipRRect(
              borderRadius: const BorderRadius.vertical(top: Radius.circular(22)),
              child: vehiclePhoto(v, 170),
            ),
            if (v.plate.isNotEmpty)
              Positioned(
                left: 12,
                bottom: 12,
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(color: Colors.black.withOpacity(0.72), borderRadius: BorderRadius.circular(8)),
                  child: Text(v.plate, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w800, letterSpacing: 1)),
                ),
              ),
          ]),
        Padding(
          padding: const EdgeInsets.all(16),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(v.name, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
                  if (compact && v.plate.isNotEmpty)
                    Text(v.plate, style: TextStyle(color: cs.onSurfaceVariant, fontWeight: FontWeight.w600)),
                ]),
              ),
              Container(
                width: 34,
                height: 34,
                decoration: BoxDecoration(color: cs.primaryContainer, shape: BoxShape.circle),
                child: Icon(Icons.chevron_right_rounded, color: cs.primary),
              ),
            ]),
            const SizedBox(height: 10),
            Wrap(spacing: 8, runSpacing: 6, children: statusPills(v)),
            const SizedBox(height: 12),
            Divider(height: 1, color: cs.outlineVariant),
            const SizedBox(height: 12),
            Row(children: [
              Icon(Icons.speed_rounded, color: cs.onSurfaceVariant),
              const SizedBox(width: 8),
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text('${fmtNum(v.odo)} km', maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16)),
                  Text('Diperbarui ${agoText(v.odoAt)}', maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(color: cs.onSurfaceVariant, fontSize: 11)),
                ]),
              ),
              FilledButton.tonalIcon(
                style: FilledButton.styleFrom(visualDensity: VisualDensity.compact, padding: const EdgeInsets.symmetric(horizontal: 10)),
                onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => TxForm(vehicleId: v.id, cat: Cat.bbm))),
                icon: const Icon(Icons.local_gas_station_rounded, size: 18),
                label: const Text('Isi BBM'),
              ),
              const SizedBox(width: 6),
              FilledButton.tonalIcon(
                style: FilledButton.styleFrom(visualDensity: VisualDensity.compact, padding: const EdgeInsets.symmetric(horizontal: 10)),
                onPressed: () => updateKmDialog(context, v),
                icon: const Icon(Icons.edit_rounded, size: 18),
                label: const Text('Update KM'),
              ),
            ]),
          ]),
        ),
      ]),
    );
  }
}

class VehicleDetail extends StatelessWidget {
  final String id;
  const VehicleDetail({super.key, required this.id});

  Widget _gauge(BuildContext context, String title, IconData icon, int interval, int lastKm, int left, int warn, String leftText) {
    final cs = Theme.of(context).colorScheme;
    final used = interval <= 0 ? 0.0 : ((interval - left) / interval).clamp(0.0, 1.0);
    final c = levelColor(left, warn);
    return Panel(
      margin: const EdgeInsets.only(bottom: 10),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Icon(icon, color: c),
          const SizedBox(width: 8),
          Expanded(child: Text(title, style: const TextStyle(fontWeight: FontWeight.w800))),
          Text(leftText, style: TextStyle(color: c, fontWeight: FontWeight.w800)),
        ]),
        const SizedBox(height: 10),
        ClipRRect(
          borderRadius: BorderRadius.circular(99),
          child: LinearProgressIndicator(value: used, minHeight: 8, color: c, backgroundColor: cs.outlineVariant),
        ),
        const SizedBox(height: 6),
        Text('Terakhir di ${fmtNum(lastKm)} km · setiap ${fmtNum(interval)} km',
            style: TextStyle(fontSize: 12, color: cs.onSurfaceVariant)),
      ]),
    );
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: store,
      builder: (context, _) {
        final v = store.vehicleById(id);
        if (v == null) return const Scaffold(body: SizedBox());
        final cs = Theme.of(context).colorScheme;
        final txs = store.txs.where((t) => t.vehicleId == id).toList()..sort((a, b) => b.date.compareTo(a.date));
        final eff = store.fuelEff(id);
        final td = v.taxDays;
        return Scaffold(
          appBar: AppBar(
            title: Text(v.name),
            actions: [
              IconButton(
                icon: const Icon(Icons.edit_rounded),
                onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => VehicleForm(edit: v))),
              ),
              IconButton(
                icon: const Icon(Icons.delete_outline_rounded),
                onPressed: () async {
                  final ok = await showDialog<bool>(
                    context: context,
                    builder: (ctx) => AlertDialog(
                      title: Text('Hapus ${v.name}?'),
                      content: const Text('Semua riwayat biaya kendaraan ini ikut terhapus.'),
                      actions: [
                        TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Batal')),
                        FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Hapus')),
                      ],
                    ),
                  );
                  if (ok == true) {
                    Navigator.pop(context);
                    await store.deleteVehicle(id);
                  }
                },
              ),
            ],
          ),
          body: ListView(
            padding: const EdgeInsets.fromLTRB(16, 4, 16, 32),
            children: [
              ClipRRect(borderRadius: BorderRadius.circular(20), child: vehiclePhoto(v, 180)),
              const SizedBox(height: 12),
              Row(children: [
                Expanded(
                  child: Text('${v.plate.isEmpty ? '' : '${v.plate} · '}${fmtNum(v.odo)} km',
                      style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16)),
                ),
                Text('diperbarui ${agoText(v.odoAt)}', style: TextStyle(fontSize: 12, color: cs.onSurfaceVariant)),
              ]),
              const SizedBox(height: 12),
              Wrap(spacing: 8, runSpacing: 8, children: [
                FilledButton.icon(
                  onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => TxForm(vehicleId: id))),
                  icon: const Icon(Icons.add_rounded),
                  label: const Text('Catat Biaya'),
                ),
                FilledButton.tonalIcon(
                  onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => TxForm(vehicleId: id, cat: Cat.bbm))),
                  icon: const Icon(Icons.local_gas_station_rounded),
                  label: const Text('Isi BBM'),
                ),
                FilledButton.tonalIcon(
                  onPressed: () => updateKmDialog(context, v),
                  icon: const Icon(Icons.speed_rounded),
                  label: const Text('Update KM'),
                ),
              ]),
              const SizedBox(height: 14),
              if (v.oilInterval > 0)
                _gauge(context, 'Ganti oli', Icons.opacity_rounded, v.oilInterval, v.lastOilKm, v.oilLeft, store.oilWarn,
                    v.oilLeft <= 0 ? 'Lewat ${fmtNum(-v.oilLeft)} km' : '${fmtNum(v.oilLeft)} km lagi'),
              if (v.serviceInterval > 0)
                _gauge(context, 'Servis berkala', Icons.build_rounded, v.serviceInterval, v.lastServiceKm, v.serviceLeft, store.serviceWarn,
                    v.serviceLeft <= 0 ? 'Lewat ${fmtNum(-v.serviceLeft)} km' : '${fmtNum(v.serviceLeft)} km lagi'),
              Panel(
                margin: const EdgeInsets.only(bottom: 10),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Row(children: [
                    Icon(Icons.receipt_long_rounded, color: td == null ? cs.onSurfaceVariant : levelColor(td, store.taxWarn)),
                    const SizedBox(width: 8),
                    const Expanded(child: Text('Pajak', style: TextStyle(fontWeight: FontWeight.w800))),
                    Text(
                      td == null ? 'Belum diatur' : (td < 0 ? 'Lewat ${-td} hari' : '$td hari lagi'),
                      style: TextStyle(fontWeight: FontWeight.w800, color: td == null ? cs.onSurfaceVariant : levelColor(td, store.taxWarn)),
                    ),
                  ]),
                  if (v.taxDue != null)
                    Padding(
                      padding: const EdgeInsets.only(top: 6),
                      child: Text('Jatuh tempo ${fmtDate(v.taxDue!)}${v.taxAmount > 0 ? ' · ${rp(v.taxAmount)}/thn' : ''}',
                          style: TextStyle(fontSize: 12, color: cs.onSurfaceVariant)),
                    ),
                ]),
              ),
              Panel(
                margin: const EdgeInsets.only(bottom: 14),
                child: Row(children: [
                  Icon(Icons.local_gas_station_rounded, color: Cat.bbm.color),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text('BBM bulan ini ${rp(store.fuelMonth(id, DateTime.now()))}',
                        style: const TextStyle(fontWeight: FontWeight.w800)),
                  ),
                  Text(eff == null ? 'km/L: -' : '${eff.toStringAsFixed(1)} km/L',
                      style: const TextStyle(fontWeight: FontWeight.w800)),
                ]),
              ),
              Text('Riwayat (${txs.length})', style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w800)),
              if (txs.isEmpty)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 20),
                  child: Center(child: Text('Belum ada catatan biaya', style: TextStyle(color: cs.onSurfaceVariant))),
                ),
              for (final t in txs) TxTile(t, showVehicle: false),
            ],
          ),
        );
      },
    );
  }
}
