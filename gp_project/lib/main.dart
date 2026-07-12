import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'core/network/api_client.dart';
import 'core/storage/auth_storage.dart';
import 'providers/auth_provider.dart';
import 'providers/chat_provider.dart';
import 'providers/notification_provider.dart';
import 'providers/room_provider.dart';
import 'providers/workspace_provider.dart';
import 'screens/splash_screen.dart';
import 'services/auth_service.dart';
import 'services/user_service.dart';
import 'services/workspace_service.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const VirtualOfficeApp());
}

class VirtualOfficeApp extends StatelessWidget {
  const VirtualOfficeApp({super.key});

  @override
  Widget build(BuildContext context) {
    final authStorage = AuthStorage();
    final apiClient = ApiClient(authStorage);

    return MultiProvider(
      providers: [
        ChangeNotifierProvider(
          create: (_) => AuthProvider(authStorage, AuthService()),
        ),
        ChangeNotifierProvider(create: (_) => ChatProvider(apiClient)),
        ChangeNotifierProvider(create: (_) => NotificationProvider(apiClient)),
        ChangeNotifierProvider(create: (_) => RoomProvider(apiClient)),
        Provider<UserService>(create: (_) => UserService(apiClient)),
        Provider<WorkspaceService>(create: (_) => WorkspaceService(apiClient)),
        ChangeNotifierProvider(
          create: (_) => WorkspaceProvider(WorkspaceService(apiClient)),
        ),
      ],
      child: MaterialApp(
        title: 'Virtual Office',
        debugShowCheckedModeBanner: false,
        theme: ThemeData(
          colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF4A6CF7)),
          useMaterial3: true,
          inputDecorationTheme: const InputDecorationTheme(filled: true),
        ),
        home: const SplashScreen(),
      ),
    );
  }
}
