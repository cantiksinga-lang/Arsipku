import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:intl/intl.dart';

final NumberFormat _nf = NumberFormat.decimalPattern('id_ID');
String fmtNum(num n) => _nf.format(n);
String rp(num n) => 'Rp ${fmtNum(n)}';
String fmtDate(DateTime d) => DateFormat('d MMM yyyy', 'id_ID').format(d);
int parseInt(String s) => int.tryParse(s.replaceAll(RegExp(r'[^0-9]'), '')) ?? 0;

const kGood = Color(0xFF1E9E55);
const kWarn = Color(0xFFE08A00);
const kBad = Color(0xFFD93636);

Color levelColor(int left, int warn) => left <= 0 ? kBad : (left <= warn ? kWarn : kGood);

String greetingFor(DateTime t) {
  final h = t.hour;
  if (h >= 5 && h <= 10) return 'Selamat Pagi';
  if (h >= 11 && h <= 14) return 'Selamat Siang';
  if (h >= 15 && h <= 17) return 'Selamat Sore';
  return 'Selamat Malam';
}

String agoText(DateTime d) {
  final n = DateTime.now();
  final days = DateTime(n.year, n.month, n.day).difference(DateTime(d.year, d.month, d.day)).inDays;
  if (days <= 0) return 'hari ini';
  if (days == 1) return 'kemarin';
  return '$days hari lalu';
}

class ThousandsFormatter extends TextInputFormatter {
  @override
  TextEditingValue formatEditUpdate(TextEditingValue oldValue, TextEditingValue newValue) {
    final d = newValue.text.replaceAll(RegExp(r'[^0-9]'), '');
    if (d.isEmpty) return const TextEditingValue();
    final t = fmtNum(int.parse(d));
    return TextEditingValue(text: t, selection: TextSelection.collapsed(offset: t.length));
  }
}

Future<DateTime?> pickDate(BuildContext context, DateTime? initial) => showDatePicker(
      context: context,
      initialDate: initial ?? DateTime.now(),
      firstDate: DateTime(2000),
      lastDate: DateTime(2100),
    );

void snack(BuildContext c, String m) =>
    ScaffoldMessenger.of(c).showSnackBar(SnackBar(content: Text(m)));

class Panel extends StatelessWidget {
  final Widget child;
  final EdgeInsets padding;
  final EdgeInsets margin;
  final VoidCallback? onTap;
  final Color? color;

  const Panel({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(16),
    this.margin = EdgeInsets.zero,
    this.onTap,
    this.color,
  });

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Container(
      margin: margin,
      decoration: BoxDecoration(
        color: color ?? cs.surface,
        borderRadius: BorderRadius.circular(22),
        border: Border.all(color: cs.outlineVariant),
      ),
      child: Material(
        type: MaterialType.transparency,
        child: InkWell(
          borderRadius: BorderRadius.circular(22),
          onTap: onTap,
          child: Padding(padding: padding, child: child),
        ),
      ),
    );
  }
}

class Pill extends StatelessWidget {
  final String text;
  final Color color;
  final IconData? icon;
  const Pill(this.text, this.color, {super.key, this.icon});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(
        color: color.withOpacity(0.16),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: color.withOpacity(0.35)),
      ),
      child: Row(mainAxisSize: MainAxisSize.min, children: [
        if (icon != null) ...[Icon(icon, size: 14, color: color), const SizedBox(width: 4)],
        Flexible(
          child: Text(text,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(color: color, fontWeight: FontWeight.w700, fontSize: 12)),
        ),
      ]),
    );
  }
}
