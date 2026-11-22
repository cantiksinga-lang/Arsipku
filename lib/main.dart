import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'forms.dart';
import 'home.dart';
import 'more.dart';
import 'report.dart';
import 'store.dart';
import 'ui.dart';

enum Phase { light, dusk, dark }

/// Pagi–siang (05.00–14.59) terang, sore (15.00–17.59) senja, malam gelap.
Phase phaseFor(DateTime t, int mode) {
  if (mode == 1) return Phase.light;
  if (mode == 2) return Phase.dusk;
  if (mode == 3) return Phase.dark;
  final h = t.hour;
  if (h >= 5 && h < 15) return Phase.light;
  if (h >= 15 && h < 18) return Phase.dusk;
  return Phase.dark;
}

ThemeData _build(Color seed, Brightness b, {required Color bg, required Color surface}) {
  final cs = ColorScheme.fromSeed(seedColor: seed, brightness: b).copyWith(surface: surface);
  return ThemeData(
    useMaterial3: true,
    colorScheme: cs,
    scaffoldBackgroundColor: bg,
    appBarTheme: AppBarTheme(backgroundColor: bg, surfaceTintColor: Colors.transparent),
    navigationBarTheme: NavigationBarThemeData(backgroundColor: surface, surfaceTintColor: Colors.transparent),
  );
}

ThemeData themeFor(Phase p) {
  if (p == Phase.light) {
    return _build(const Color(0xFF0E7FC0), Brightness.light, bg: const Color(0xFFF1F6FB), surface: Colors.white);
  }
  if (p == Phase.dusk) {
    return _build(const Color(0xFFE2703A), Brightness.light, bg: const Color(0xFFFFE9D6), surface: const Color(0xFFFFF7EF));
  }
  return _build(const Color(0xFF3AA6E6), Brightness.dark, bg: const Color(0xFF0D1319), surface: const Color(0xFF16202A));
}

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await initializeDateFormatting('id_ID');
  await store.load();
  runApp(const GarasiApp());
}

class GarasiApp extends StatefulWidget {
  const GarasiApp({super.key});
  @override
  State<GarasiApp> createState() => _GarasiAppState();
}

class _GarasiAppState extends State<GarasiApp> {
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    // cek pergantian waktu (tema otomatis & sapaan) tiap 30 detik
    _timer = Timer.periodic(const Duration(seconds: 30), (_) => setState(() {}));
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: store,
      builder: (context, _) {
        final phase = phaseFor(DateTime.now(), store.themeMode);
        return MaterialApp(
          title: 'GarasiKu',
          debugShowCheckedModeBanner: false,
          theme: themeFor(phase),
          home: const Shell(),
        );
      },
    );
  }
}

class Shell extends StatefulWidget {
  const Shell({super.key});
  @override
  State<Shell> createState() => _ShellState();
}

class _ShellState extends State<Shell> {
  int tab = 0;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _askName());
  }

  Future<void> _askName() async {
    final n = store.userName;
    if (n != null && n.trim().isNotEmpty) {
      Native.requestNotif();
      return;
    }
    if (!mounted) return;
    final c = TextEditingController();
    await showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => PopScope(
        canPop: false,
        child: AlertDialog(
          title: const Text('Selamat datang di GarasiKu 👋'),
          content: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
            const Text('Siapa nama Anda?'),
            const SizedBox(height: 12),
            TextField(
              controller: c,
              autofocus: true,
              textCapitalization: TextCapitalization.words,
              decoration: const InputDecoration(labelText: 'Nama', border: OutlineInputBorder()),
              onSubmitted: (_) {
                if (c.text.trim().isNotEmpty) Navigator.pop(ctx);
              },
            ),
          ]),
          actions: [
            FilledButton(
              onPressed: () {
                if (c.text.trim().isNotEmpty) Navigator.pop(ctx);
              },
              child: const Text('Mulai'),
            ),
          ],
        ),
      ),
    );
    await store.setUser(c.text.trim());
    Native.requestNotif();
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: store,
      builder: (context, _) {
        final dark = Theme.of(context).brightness == Brightness.dark;
        final alerts = store.alertCount();
        return AnnotatedRegion<SystemUiOverlayStyle>(
          value: (dark ? SystemUiOverlayStyle.light : SystemUiOverlayStyle.dark).copyWith(statusBarColor: Colors.transparent),
          child: Scaffold(
            body: IndexedStack(index: tab, children: const [HomeTab(), ReportTab(), SimTab()]),
            floatingActionButton: tab < 2
                ? FloatingActionButton.extended(
                    onPressed: () {
                      if (store.vehicles.isEmpty) {
                        snack(context, 'Tambah kendaraan dulu');
                        Navigator.push(context, MaterialPageRoute(builder: (_) => const VehicleForm()));
                      } else {
                        Navigator.push(context, MaterialPageRoute(builder: (_) => const TxForm()));
                      }
                    },
                    icon: const Icon(Icons.add_rounded),
                    label: const Text('Catat'),
                  )
                : null,
            bottomNavigationBar: NavigationBar(
              selectedIndex: tab,
              onDestinationSelected: (i) => setState(() => tab = i),
              destinations: [
                NavigationDestination(
                  icon: Badge(isLabelVisible: alerts > 0, label: Text('$alerts'), child: const Icon(Icons.two_wheeler_rounded)),
                  label: 'Kendaraan',
                ),
                const NavigationDestination(icon: Icon(Icons.bar_chart_rounded), label: 'Laporan'),
                const NavigationDestination(icon: Icon(Icons.badge_rounded), label: 'SIM Saya'),
              ],
            ),
          ),
        );
      },
    );
  }
}
