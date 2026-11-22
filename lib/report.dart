import 'dart:math';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'forms.dart';
import 'models.dart';
import 'store.dart';
import 'ui.dart';

class ReportTab extends StatefulWidget {
  const ReportTab({super.key});
  @override
  State<ReportTab> createState() => _ReportTabState();
}

class _ReportTabState extends State<ReportTab> {
  int mode = 0; // 0 bulan, 1 tahun, 2 semua, 3 rentang
  DateTime anchor = DateTime.now();
  DateTimeRange? range;
  String? vid;
  String q = '';

  List<DateTime>? _bounds() {
    switch (mode) {
      case 0:
        return [DateTime(anchor.year, anchor.month, 1), DateTime(anchor.year, anchor.month + 1, 1)];
      case 1:
        return [DateTime(anchor.year, 1, 1), DateTime(anchor.year + 1, 1, 1)];
      case 3:
        final r = range;
        if (r == null) return [DateTime(1900), DateTime(1900)];
        return [DateTime(r.start.year, r.start.month, r.start.day), DateTime(r.end.year, r.end.month, r.end.day + 1)];
      default:
        return null;
    }
  }

  String _label() {
    switch (mode) {
      case 0:
        return DateFormat('MMMM yyyy', 'id_ID').format(anchor);
      case 1:
        return '${anchor.year}';
      case 2:
        return 'Semua waktu';
      default:
        final r = range;
        return r == null ? 'Pilih rentang tanggal' : '${fmtDate(r.start)} – ${fmtDate(r.end)}';
    }
  }

  List<Tx> _filtered() {
    final b = _bounds();
    final s = q.toLowerCase();
    final list = store.txs.where((t) {
      if (b != null && (t.date.isBefore(b[0]) || !t.date.isBefore(b[1]))) return false;
      if (vid != null && t.vehicleId != vid) return false;
      if (s.isNotEmpty) {
        final v = store.vehicleById(t.vehicleId);
        final hay = '${t.title} ${t.note} ${t.cat.label} ${v?.name ?? ''} ${v?.plate ?? ''} ${t.amount}'.toLowerCase();
        if (!hay.contains(s)) return false;
      }
      return true;
    }).toList();
    list.sort((a, b) => b.date.compareTo(a.date));
    return list;
  }

  Future<void> _pickRange() async {
    final r = await showDateRangePicker(
      context: context,
      firstDate: DateTime(2000),
      lastDate: DateTime(2100),
      initialDateRange: range,
    );
    if (r != null) setState(() => range = r);
  }

  void _shift(int d) {
    setState(() {
      if (mode == 0) anchor = DateTime(anchor.year, anchor.month + d, 1);
      if (mode == 1) anchor = DateTime(anchor.year + d, 1, 1);
    });
  }

  Widget _yearBars(List<Tx> list, ColorScheme cs) {
    final sums = List<int>.filled(12, 0);
    for (final t in list) {
      sums[t.date.month - 1] += t.amount;
    }
    final mx = sums.reduce(max);
    const names = ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O', 'N', 'D'];
    return SizedBox(
      height: 110,
      child: Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
        for (var i = 0; i < 12; i++)
          Expanded(
            child: Column(mainAxisAlignment: MainAxisAlignment.end, children: [
              Container(
                height: mx == 0 ? 2 : 80 * sums[i] / mx + 2,
                margin: const EdgeInsets.symmetric(horizontal: 3),
                decoration: BoxDecoration(color: cs.primary, borderRadius: BorderRadius.circular(4)),
              ),
              const SizedBox(height: 4),
              Text(names[i], style: const TextStyle(fontSize: 10)),
            ]),
          ),
      ]),
    );
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final list = _filtered();
    final total = list.fold<int>(0, (s, t) => s + t.amount);
    final byCat = <Cat, int>{};
    for (final t in list) {
      byCat[t.cat] = (byCat[t.cat] ?? 0) + t.amount;
    }
    final cats = byCat.entries.toList()..sort((a, b) => b.value.compareTo(a.value));
    final showNav = mode == 0 || mode == 1;

    return SafeArea(
      child: ListView(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 96),
        children: [
          const Text('Laporan Keuangan', style: TextStyle(fontSize: 24, fontWeight: FontWeight.w800)),
          const SizedBox(height: 12),
          SegmentedButton<int>(
            showSelectedIcon: false,
            segments: const [
              ButtonSegment(value: 0, label: Text('Bulan')),
              ButtonSegment(value: 1, label: Text('Tahun')),
              ButtonSegment(value: 2, label: Text('Semua')),
              ButtonSegment(value: 3, label: Text('Rentang')),
            ],
            selected: {mode},
            onSelectionChanged: (s) {
              setState(() => mode = s.first);
              if (mode == 3 && range == null) _pickRange();
            },
          ),
          const SizedBox(height: 10),
          Row(children: [
            if (showNav) IconButton(onPressed: () => _shift(-1), icon: const Icon(Icons.chevron_left_rounded)),
            Expanded(
              child: TextButton(
                onPressed: mode == 3 ? _pickRange : null,
                child: Text(_label(),
                    textAlign: TextAlign.center,
                    style: TextStyle(fontSize: 16, fontWeight: FontWeight.w800, color: cs.onSurface)),
              ),
            ),
            if (showNav) IconButton(onPressed: () => _shift(1), icon: const Icon(Icons.chevron_right_rounded)),
          ]),
          TextField(
            onChanged: (v) => setState(() => q = v.trim()),
            decoration: InputDecoration(
              hintText: 'Cari sparepart, kendaraan, catatan, nominal…',
              prefixIcon: const Icon(Icons.search_rounded),
              filled: true,
              fillColor: cs.surface,
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(99), borderSide: BorderSide(color: cs.outlineVariant)),
              enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(99), borderSide: BorderSide(color: cs.outlineVariant)),
              contentPadding: const EdgeInsets.symmetric(vertical: 0),
            ),
          ),
          if (store.vehicles.length > 1)
            SizedBox(
              height: 52,
              child: ListView(scrollDirection: Axis.horizontal, children: [
                Padding(
                  padding: const EdgeInsets.only(right: 8, top: 8),
                  child: ChoiceChip(label: const Text('Semua kendaraan'), selected: vid == null, onSelected: (_) => setState(() => vid = null)),
                ),
                for (final v in store.vehicles)
                  Padding(
                    padding: const EdgeInsets.only(right: 8, top: 8),
                    child: ChoiceChip(label: Text(v.name), selected: vid == v.id, onSelected: (_) => setState(() => vid = v.id)),
                  ),
              ]),
            ),
          const SizedBox(height: 12),
          Panel(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text('Total pengeluaran', style: TextStyle(color: cs.onSurfaceVariant, fontWeight: FontWeight.w600)),
              const SizedBox(height: 4),
              Text(rp(total), style: const TextStyle(fontSize: 28, fontWeight: FontWeight.w800)),
              Text('${list.length} transaksi', style: TextStyle(color: cs.onSurfaceVariant, fontSize: 12)),
              if (mode == 1) ...[const SizedBox(height: 14), _yearBars(list, cs)],
              if (cats.isNotEmpty) const SizedBox(height: 14),
              for (final e in cats)
                Padding(
                  padding: const EdgeInsets.only(bottom: 10),
                  child: Column(children: [
                    Row(children: [
                      Icon(e.key.icon, size: 16, color: e.key.color),
                      const SizedBox(width: 6),
                      Expanded(child: Text(e.key.label, style: const TextStyle(fontWeight: FontWeight.w600))),
                      Text(rp(e.value), style: const TextStyle(fontWeight: FontWeight.w800)),
                    ]),
                    const SizedBox(height: 4),
                    ClipRRect(
                      borderRadius: BorderRadius.circular(99),
                      child: LinearProgressIndicator(
                        value: total == 0 ? 0 : e.value / total,
                        minHeight: 6,
                        color: e.key.color,
                        backgroundColor: cs.outlineVariant,
                      ),
                    ),
                  ]),
                ),
            ]),
          ),
          const SizedBox(height: 14),
          Text('Transaksi', style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w800)),
          if (list.isEmpty)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 28),
              child: Center(
                child: Text(mode == 3 && range == null ? 'Pilih rentang tanggal dulu' : 'Tidak ada transaksi',
                    style: TextStyle(color: cs.onSurfaceVariant)),
              ),
            ),
          for (final t in list) TxTile(t),
        ],
      ),
    );
  }
}
