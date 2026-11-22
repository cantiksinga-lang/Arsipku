import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:image_picker/image_picker.dart';
import 'package:path_provider/path_provider.dart';
import 'models.dart';
import 'ocr.dart';
import 'store.dart';
import 'ui.dart';

String vehicleLabel(Vehicle v) => v.plate.isEmpty ? v.name : '${v.name} · ${v.plate}';

Future<void> updateKmDialog(BuildContext context, Vehicle v) async {
  final c = TextEditingController(text: fmtNum(v.odo));
  final ok = await showDialog<bool>(
    context: context,
    builder: (ctx) => AlertDialog(
      title: Text('Update KM · ${v.name}'),
      content: TextField(
        controller: c,
        autofocus: true,
        keyboardType: TextInputType.number,
        inputFormatters: [LengthLimitingTextInputFormatter(11), ThousandsFormatter()],
        decoration: const InputDecoration(labelText: 'KM saat ini', suffixText: 'km', border: OutlineInputBorder()),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Batal')),
        FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Simpan')),
      ],
    ),
  );
  if (ok == true) await store.updateKm(v.id, parseInt(c.text));
}

class TxTile extends StatelessWidget {
  final Tx t;
  final bool showVehicle;
  const TxTile(this.t, {super.key, this.showVehicle = true});

  @override
  Widget build(BuildContext context) {
    final v = store.vehicleById(t.vehicleId);
    final sub = <String>[
      if (showVehicle) v?.name ?? '-',
      fmtDate(t.date),
      if (t.km != null) '${fmtNum(t.km!)} km',
      if (t.liters != null) '${t.liters!.toStringAsFixed(2)} L',
    ].join(' · ');
    return ListTile(
      contentPadding: EdgeInsets.zero,
      leading: CircleAvatar(
        backgroundColor: t.cat.color.withOpacity(0.15),
        child: Icon(t.cat.icon, color: t.cat.color, size: 20),
      ),
      title: Text(t.title, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w600)),
      subtitle: Text(sub, maxLines: 1, overflow: TextOverflow.ellipsis),
      trailing: Text(rp(t.amount), style: const TextStyle(fontWeight: FontWeight.w800)),
      onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => TxForm(edit: t))),
    );
  }
}

// ======================= FORM TRANSAKSI + OCR =======================
class TxForm extends StatefulWidget {
  final Tx? edit;
  final String? vehicleId;
  final Cat? cat;
  const TxForm({super.key, this.edit, this.vehicleId, this.cat});

  @override
  State<TxForm> createState() => _TxFormState();
}

class _TxFormState extends State<TxForm> {
  final _amount = TextEditingController();
  final _title = TextEditingController();
  final _km = TextEditingController();
  final _liters = TextEditingController();
  final _note = TextEditingController();
  final _picker = ImagePicker();
  String? vid;
  Cat cat = Cat.servis;
  DateTime date = DateTime.now();
  bool scanning = false;
  String raw = '';

  @override
  void initState() {
    super.initState();
    final e = widget.edit;
    if (e != null) {
      vid = e.vehicleId;
      cat = e.cat;
      date = e.date;
      _amount.text = fmtNum(e.amount);
      _title.text = e.title;
      if (e.km != null) _km.text = fmtNum(e.km!);
      if (e.liters != null) _liters.text = e.liters!.toString();
      _note.text = e.note;
    } else {
      vid = widget.vehicleId ?? (store.vehicles.isNotEmpty ? store.vehicles.first.id : null);
      cat = widget.cat ?? Cat.servis;
      final v = store.vehicleById(vid);
      if (v != null && v.odo > 0) _km.text = fmtNum(v.odo);
    }
  }

  @override
  void dispose() {
    _amount.dispose();
    _title.dispose();
    _km.dispose();
    _liters.dispose();
    _note.dispose();
    super.dispose();
  }

  Future<void> _scan(ImageSource src) async {
    final x = await _picker.pickImage(source: src, imageQuality: 90, maxWidth: 2200);
    if (x == null) return;
    setState(() => scanning = true);
    try {
      final r = await Ocr.scan(x.path);
      if (!mounted) return;
      setState(() {
        raw = r.raw;
        if (r.amount != null) _amount.text = fmtNum(r.amount!);
        if (r.title != null && r.title!.isNotEmpty) _title.text = r.title!;
        if (r.date != null) date = r.date!;
        if (r.liters != null) _liters.text = r.liters!.toStringAsFixed(2);
        if (r.km != null) _km.text = fmtNum(r.km!);
        if (r.cat != null) cat = r.cat!;
      });
      snack(context, r.amount == null && r.title == null
          ? 'Teks tidak terbaca jelas, coba foto lebih dekat/terang.'
          : 'Terbaca: ${r.amount != null ? rp(r.amount!) : '-'}. Periksa lagi sebelum menyimpan.');
    } catch (e) {
      if (mounted) snack(context, 'Gagal membaca gambar');
    } finally {
      if (mounted) setState(() => scanning = false);
    }
  }

  Future<void> _save() async {
    if (vid == null) {
      snack(context, 'Tambah kendaraan dulu');
      return;
    }
    final amount = parseInt(_amount.text);
    if (amount <= 0) {
      snack(context, 'Isi nominal biaya');
      return;
    }
    final t = Tx(
      id: widget.edit?.id ?? newId(),
      vehicleId: vid!,
      cat: cat,
      date: date,
      amount: amount,
      title: _title.text.trim().isEmpty ? cat.label : _title.text.trim(),
      km: _km.text.trim().isEmpty ? null : parseInt(_km.text),
      liters: cat.isFuel ? double.tryParse(_liters.text.trim().replaceAll(',', '.')) : null,
      note: _note.text.trim(),
    );
    await store.saveTx(t);
    if (mounted) Navigator.pop(context);
  }

  Future<void> _delete() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Hapus transaksi?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Batal')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Hapus')),
        ],
      ),
    );
    if (ok == true) {
      await store.deleteTx(widget.edit!.id);
      if (mounted) Navigator.pop(context);
    }
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.edit == null ? 'Catat Biaya' : 'Ubah Transaksi'),
        actions: [
          if (widget.edit != null) IconButton(icon: const Icon(Icons.delete_outline_rounded), onPressed: _delete),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
        children: [
          Panel(
            color: cs.primaryContainer,
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Row(children: [
                Icon(Icons.document_scanner_rounded, color: cs.onPrimaryContainer),
                const SizedBox(width: 8),
                Expanded(
                  child: Text('Scan struk / nota (OCR)',
                      style: TextStyle(fontWeight: FontWeight.w800, color: cs.onPrimaryContainer)),
                ),
              ]),
              const SizedBox(height: 4),
              Text('Foto atau pilih gambar nota, nominal, nama sparepart, tanggal, dll terisi otomatis.',
                  style: TextStyle(fontSize: 12, color: cs.onPrimaryContainer)),
              const SizedBox(height: 10),
              if (scanning)
                const LinearProgressIndicator()
              else
                Row(children: [
                  Expanded(
                    child: FilledButton.icon(
                      onPressed: () => _scan(ImageSource.camera),
                      icon: const Icon(Icons.photo_camera_rounded),
                      label: const Text('Kamera'),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: FilledButton.tonalIcon(
                      onPressed: () => _scan(ImageSource.gallery),
                      icon: const Icon(Icons.photo_library_rounded),
                      label: const Text('Galeri'),
                    ),
                  ),
                ]),
            ]),
          ),
          if (raw.isNotEmpty)
            Theme(
              data: Theme.of(context).copyWith(dividerColor: Colors.transparent),
              child: ExpansionTile(
                tilePadding: EdgeInsets.zero,
                title: const Text('Teks hasil OCR', style: TextStyle(fontSize: 13)),
                children: [Align(alignment: Alignment.centerLeft, child: SelectableText(raw, style: const TextStyle(fontSize: 12)))],
              ),
            ),
          const SizedBox(height: 14),
          DropdownButtonFormField<String>(
            value: vid,
            decoration: const InputDecoration(labelText: 'Kendaraan', border: OutlineInputBorder()),
            items: store.vehicles.map((v) => DropdownMenuItem(value: v.id, child: Text(vehicleLabel(v)))).toList(),
            onChanged: (x) => setState(() {
              vid = x;
              if (widget.edit == null) {
                final v = store.vehicleById(x);
                _km.text = (v != null && v.odo > 0) ? fmtNum(v.odo) : '';
              }
            }),
          ),
          const SizedBox(height: 14),
          Wrap(
            spacing: 8,
            runSpacing: 4,
            children: Cat.all
                .map((c) => ChoiceChip(
                      avatar: Icon(c.icon, size: 18),
                      label: Text(c.label),
                      selected: cat == c,
                      onSelected: (_) => setState(() => cat = c),
                    ))
                .toList(),
          ),
          const SizedBox(height: 14),
          TextField(
            controller: _amount,
            keyboardType: TextInputType.number,
            inputFormatters: [LengthLimitingTextInputFormatter(13), ThousandsFormatter()],
            decoration: const InputDecoration(labelText: 'Nominal', prefixText: 'Rp ', border: OutlineInputBorder()),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _title,
            textCapitalization: TextCapitalization.sentences,
            decoration: InputDecoration(
              labelText: cat.isFuel ? 'Jenis BBM' : 'Nama sparepart / jasa',
              border: const OutlineInputBorder(),
            ),
          ),
          const SizedBox(height: 12),
          OutlinedButton.icon(
            style: OutlinedButton.styleFrom(minimumSize: const Size.fromHeight(52), alignment: Alignment.centerLeft),
            onPressed: () async {
              final d = await pickDate(context, date);
              if (d != null) setState(() => date = d);
            },
            icon: const Icon(Icons.event_rounded),
            label: Text('Tanggal: ${fmtDate(date)}'),
          ),
          const SizedBox(height: 12),
          Row(children: [
            Expanded(
              child: TextField(
                controller: _km,
                keyboardType: TextInputType.number,
                inputFormatters: [LengthLimitingTextInputFormatter(11), ThousandsFormatter()],
                decoration: const InputDecoration(labelText: 'KM saat ini', suffixText: 'km', border: OutlineInputBorder()),
              ),
            ),
            if (cat.isFuel) ...[
              const SizedBox(width: 12),
              Expanded(
                child: TextField(
                  controller: _liters,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  decoration: const InputDecoration(labelText: 'Liter', suffixText: 'L', border: OutlineInputBorder()),
                ),
              ),
            ],
          ]),
          const SizedBox(height: 12),
          TextField(
            controller: _note,
            maxLines: 2,
            decoration: const InputDecoration(labelText: 'Catatan (opsional)', border: OutlineInputBorder()),
          ),
          const SizedBox(height: 20),
          FilledButton.icon(
            style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(52)),
            onPressed: _save,
            icon: const Icon(Icons.check_rounded),
            label: const Text('Simpan'),
          ),
          if (cat == Cat.oli || cat == Cat.servis)
            Padding(
              padding: const EdgeInsets.only(top: 10),
              child: Text(
                cat == Cat.oli
                    ? 'KM ini akan dipakai sebagai patokan penghitungan jadwal ganti oli berikutnya.'
                    : 'KM ini akan dipakai sebagai patokan penghitungan jadwal servis berikutnya.',
                style: TextStyle(fontSize: 12, color: cs.onSurfaceVariant),
              ),
            ),
        ],
      ),
    );
  }
}

// ======================= FORM KENDARAAN =======================
class VehicleForm extends StatefulWidget {
  final Vehicle? edit;
  const VehicleForm({super.key, this.edit});

  @override
  State<VehicleForm> createState() => _VehicleFormState();
}

class _VehicleFormState extends State<VehicleForm> {
  final _name = TextEditingController();
  final _plate = TextEditingController();
  final _odo = TextEditingController();
  final _taxAmount = TextEditingController();
  final _oilInt = TextEditingController(text: '2.000');
  final _lastOil = TextEditingController();
  final _svcInt = TextEditingController(text: '6.000');
  final _lastSvc = TextEditingController();
  String type = 'Motor';
  DateTime? taxDue;
  String? photo;
  bool showWidget = true;

  @override
  void initState() {
    super.initState();
    final e = widget.edit;
    if (e != null) {
      _name.text = e.name;
      _plate.text = e.plate;
      _odo.text = fmtNum(e.odo);
      _taxAmount.text = e.taxAmount > 0 ? fmtNum(e.taxAmount) : '';
      _oilInt.text = fmtNum(e.oilInterval);
      _lastOil.text = fmtNum(e.lastOilKm);
      _svcInt.text = fmtNum(e.serviceInterval);
      _lastSvc.text = fmtNum(e.lastServiceKm);
      type = e.type;
      taxDue = e.taxDue;
      photo = e.photo;
      showWidget = e.showWidget;
    }
  }

  @override
  void dispose() {
    for (final c in [_name, _plate, _odo, _taxAmount, _oilInt, _lastOil, _svcInt, _lastSvc]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _pickPhoto(ImageSource src) async {
    final x = await ImagePicker().pickImage(source: src, imageQuality: 85, maxWidth: 1600);
    if (x == null) return;
    final dir = await getApplicationDocumentsDirectory();
    final f = await File(x.path).copy('${dir.path}/veh_${newId()}.jpg');
    setState(() => photo = f.path);
  }

  Future<void> _save() async {
    if (_name.text.trim().isEmpty) {
      snack(context, 'Isi nama kendaraan');
      return;
    }
    final e = widget.edit;
    final odo = parseInt(_odo.text);
    final v = Vehicle(
      id: e?.id ?? newId(),
      name: _name.text.trim(),
      plate: _plate.text.trim().toUpperCase(),
      type: type,
      photo: photo,
      odo: odo,
      odoAt: (e != null && e.odo == odo) ? e.odoAt : DateTime.now(),
      taxDue: taxDue,
      taxAmount: parseInt(_taxAmount.text),
      oilInterval: parseInt(_oilInt.text),
      lastOilKm: _lastOil.text.trim().isEmpty ? odo : parseInt(_lastOil.text),
      serviceInterval: parseInt(_svcInt.text),
      lastServiceKm: _lastSvc.text.trim().isEmpty ? odo : parseInt(_lastSvc.text),
      showWidget: showWidget,
    );
    await store.saveVehicle(v);
    if (mounted) Navigator.pop(context);
  }

  Widget _num(TextEditingController c, String label, {String? suffix, String? prefix, String? helper}) => Padding(
        padding: const EdgeInsets.only(bottom: 12),
        child: TextField(
          controller: c,
          keyboardType: TextInputType.number,
          inputFormatters: [LengthLimitingTextInputFormatter(13), ThousandsFormatter()],
          decoration: InputDecoration(
            labelText: label,
            suffixText: suffix,
            prefixText: prefix,
            helperText: helper,
            border: const OutlineInputBorder(),
          ),
        ),
      );

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final p = photo;
    return Scaffold(
      appBar: AppBar(title: Text(widget.edit == null ? 'Tambah Kendaraan' : 'Ubah Kendaraan')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(18),
            child: Container(
              height: 150,
              width: double.infinity,
              color: cs.surfaceContainerHighest,
              child: (p != null && File(p).existsSync())
                  ? Image.file(File(p), fit: BoxFit.cover, cacheWidth: 900)
                  : Icon(type == 'Mobil' ? Icons.directions_car_rounded : Icons.two_wheeler_rounded,
                      size: 64, color: cs.onSurfaceVariant),
            ),
          ),
          Row(children: [
            TextButton.icon(
                onPressed: () => _pickPhoto(ImageSource.camera),
                icon: const Icon(Icons.photo_camera_rounded),
                label: const Text('Kamera')),
            TextButton.icon(
                onPressed: () => _pickPhoto(ImageSource.gallery),
                icon: const Icon(Icons.photo_library_rounded),
                label: const Text('Galeri')),
            const Spacer(),
            if (photo != null) TextButton(onPressed: () => setState(() => photo = null), child: const Text('Hapus foto')),
          ]),
          const SizedBox(height: 4),
          TextField(
            controller: _name,
            textCapitalization: TextCapitalization.words,
            decoration: const InputDecoration(labelText: 'Nama kendaraan', hintText: 'Yamaha NMAX Neo S', border: OutlineInputBorder()),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _plate,
            textCapitalization: TextCapitalization.characters,
            decoration: const InputDecoration(labelText: 'Plat nomor', hintText: 'R 1234 XD', border: OutlineInputBorder()),
          ),
          const SizedBox(height: 12),
          SegmentedButton<String>(
            segments: const [
              ButtonSegment(value: 'Motor', label: Text('Motor'), icon: Icon(Icons.two_wheeler_rounded)),
              ButtonSegment(value: 'Mobil', label: Text('Mobil'), icon: Icon(Icons.directions_car_rounded)),
            ],
            selected: {type},
            onSelectionChanged: (s) => setState(() => type = s.first),
          ),
          const SizedBox(height: 16),
          _num(_odo, 'KM saat ini', suffix: 'km'),
          Text('Pajak', style: Theme.of(context).textTheme.titleSmall),
          const SizedBox(height: 8),
          OutlinedButton.icon(
            style: OutlinedButton.styleFrom(minimumSize: const Size.fromHeight(52), alignment: Alignment.centerLeft),
            onPressed: () async {
              final d = await pickDate(context, taxDue);
              if (d != null) setState(() => taxDue = d);
            },
            icon: const Icon(Icons.event_rounded),
            label: Text(taxDue == null ? 'Tanggal jatuh tempo pajak' : 'Jatuh tempo: ${fmtDate(taxDue!)}'),
          ),
          const SizedBox(height: 12),
          _num(_taxAmount, 'Estimasi pajak per tahun', prefix: 'Rp '),
          Text('Jadwal ganti oli', style: Theme.of(context).textTheme.titleSmall),
          const SizedBox(height: 8),
          _num(_oilInt, 'Ganti oli setiap', suffix: 'km'),
          _num(_lastOil, 'KM terakhir ganti oli', suffix: 'km', helper: 'Kosongkan = pakai KM saat ini'),
          Text('Jadwal servis', style: Theme.of(context).textTheme.titleSmall),
          const SizedBox(height: 8),
          _num(_svcInt, 'Servis setiap', suffix: 'km'),
          _num(_lastSvc, 'KM terakhir servis', suffix: 'km', helper: 'Kosongkan = pakai KM saat ini'),
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: const Text('Tampilkan di widget layar utama'),
            value: showWidget,
            onChanged: (v) => setState(() => showWidget = v),
          ),
          const SizedBox(height: 12),
          FilledButton.icon(
            style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(52)),
            onPressed: _save,
            icon: const Icon(Icons.check_rounded),
            label: const Text('Simpan'),
          ),
        ],
      ),
    );
  }
}
