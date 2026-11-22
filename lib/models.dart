import 'package:flutter/material.dart';

String newId() => DateTime.now().microsecondsSinceEpoch.toString();

class Cat {
  final String key;
  final String label;
  final IconData icon;
  final Color color;
  const Cat(this.key, this.label, this.icon, this.color);

  static const servis = Cat('servis', 'Servis', Icons.build_rounded, Color(0xFF0E7FC0));
  static const oli = Cat('oli', 'Ganti Oli', Icons.opacity_rounded, Color(0xFFE08A00));
  static const sparepart = Cat('sparepart', 'Sparepart', Icons.settings_rounded, Color(0xFF7B5CD6));
  static const bbm = Cat('bbm', 'BBM', Icons.local_gas_station_rounded, Color(0xFFD9822B));
  static const pajak = Cat('pajak', 'Pajak/STNK', Icons.receipt_long_rounded, Color(0xFF1E9E55));
  static const lain = Cat('lain', 'Lainnya', Icons.more_horiz_rounded, Color(0xFF6B7A8C));
  static const all = [servis, oli, sparepart, bbm, pajak, lain];

  static Cat of(String? k) => all.firstWhere((c) => c.key == k, orElse: () => lain);
  bool get isFuel => key == 'bbm';
}

class Vehicle {
  String id;
  String name;
  String plate;
  String type; // Motor / Mobil
  String? photo;
  int odo;
  DateTime odoAt;
  DateTime? taxDue;
  int taxAmount;
  int oilInterval;
  int lastOilKm;
  int serviceInterval;
  int lastServiceKm;
  bool showWidget;

  Vehicle({
    required this.id,
    required this.name,
    this.plate = '',
    this.type = 'Motor',
    this.photo,
    this.odo = 0,
    DateTime? odoAt,
    this.taxDue,
    this.taxAmount = 0,
    this.oilInterval = 2000,
    this.lastOilKm = 0,
    this.serviceInterval = 6000,
    this.lastServiceKm = 0,
    this.showWidget = true,
  }) : odoAt = odoAt ?? DateTime.now();

  int get oilLeft => lastOilKm + oilInterval - odo;
  int get serviceLeft => lastServiceKm + serviceInterval - odo;

  int? get taxDays {
    final d = taxDue;
    if (d == null) return null;
    final n = DateTime.now();
    return DateTime(d.year, d.month, d.day).difference(DateTime(n.year, n.month, n.day)).inDays;
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'plate': plate,
        'type': type,
        'photo': photo,
        'odo': odo,
        'odoAt': odoAt.toIso8601String(),
        'taxDue': taxDue?.toIso8601String(),
        'taxAmount': taxAmount,
        'oilInterval': oilInterval,
        'lastOilKm': lastOilKm,
        'serviceInterval': serviceInterval,
        'lastServiceKm': lastServiceKm,
        'showWidget': showWidget,
      };

  factory Vehicle.fromJson(Map<String, dynamic> j) => Vehicle(
        id: j['id'] as String,
        name: (j['name'] ?? '') as String,
        plate: (j['plate'] ?? '') as String,
        type: (j['type'] ?? 'Motor') as String,
        photo: j['photo'] as String?,
        odo: (j['odo'] ?? 0) as int,
        odoAt: DateTime.tryParse('${j['odoAt']}'),
        taxDue: j['taxDue'] == null ? null : DateTime.tryParse('${j['taxDue']}'),
        taxAmount: (j['taxAmount'] ?? 0) as int,
        oilInterval: (j['oilInterval'] ?? 0) as int,
        lastOilKm: (j['lastOilKm'] ?? 0) as int,
        serviceInterval: (j['serviceInterval'] ?? 0) as int,
        lastServiceKm: (j['lastServiceKm'] ?? 0) as int,
        showWidget: (j['showWidget'] ?? true) as bool,
      );
}

class Tx {
  String id;
  String vehicleId;
  Cat cat;
  DateTime date;
  int amount;
  String title;
  int? km;
  double? liters;
  String note;

  Tx({
    required this.id,
    required this.vehicleId,
    required this.cat,
    required this.date,
    required this.amount,
    this.title = '',
    this.km,
    this.liters,
    this.note = '',
  });

  Map<String, dynamic> toJson() => {
        'id': id,
        'vehicleId': vehicleId,
        'cat': cat.key,
        'date': date.toIso8601String(),
        'amount': amount,
        'title': title,
        'km': km,
        'liters': liters,
        'note': note,
      };

  factory Tx.fromJson(Map<String, dynamic> j) => Tx(
        id: j['id'] as String,
        vehicleId: j['vehicleId'] as String,
        cat: Cat.of(j['cat'] as String?),
        date: DateTime.tryParse('${j['date']}') ?? DateTime.now(),
        amount: (j['amount'] ?? 0) as int,
        title: (j['title'] ?? '') as String,
        km: j['km'] as int?,
        liters: (j['liters'] as num?)?.toDouble(),
        note: (j['note'] ?? '') as String,
      );
}

class SimItem {
  String id;
  String type;
  String name;
  String number;
  DateTime expiry;

  SimItem({required this.id, required this.type, this.name = '', this.number = '', required this.expiry});

  int get daysLeft {
    final n = DateTime.now();
    return DateTime(expiry.year, expiry.month, expiry.day).difference(DateTime(n.year, n.month, n.day)).inDays;
  }

  Map<String, dynamic> toJson() =>
      {'id': id, 'type': type, 'name': name, 'number': number, 'expiry': expiry.toIso8601String()};

  factory SimItem.fromJson(Map<String, dynamic> j) => SimItem(
        id: j['id'] as String,
        type: (j['type'] ?? 'SIM C') as String,
        name: (j['name'] ?? '') as String,
        number: (j['number'] ?? '') as String,
        expiry: DateTime.tryParse('${j['expiry']}') ?? DateTime.now(),
      );
}
