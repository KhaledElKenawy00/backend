import 'dart:developer' as dev;
import '../storage/auth_storage.dart';

class ApiClient {
  final AuthStorage _authStorage;

  ApiClient(this._authStorage);

  Future<Map<String, String>> userServiceHeaders() async {
    final token = await _authStorage.getToken();
    final headers = {
      'Authorization': 'Bearer ${token ?? ''}',
      'Content-Type': 'application/json',
    };
    final preview = token != null ? '${token.substring(0, token.length.clamp(0, 20))}...' : 'NULL';
    dev.log('[API_CLIENT] userServiceHeaders — token=$preview', name: 'ApiClient');
    return headers;
  }

  Future<Map<String, String>> chatServiceHeaders() async {
    final userId = await _authStorage.getUserId();
    final role = await _authStorage.getUserRole();
    final headers = {
      'X-User-Id': userId ?? '',
      'X-User-Role': role ?? 'USER',
      'Content-Type': 'application/json',
    };
    dev.log('[API_CLIENT] chatServiceHeaders — X-User-Id=$userId  X-User-Role=$role', name: 'ApiClient');
    return headers;
  }

  Future<Map<String, String>> notifServiceHeaders() async {
    final userId = await _authStorage.getUserId();
    final role = await _authStorage.getUserRole();
    final headers = {
      'X-User-Id': userId ?? '',
      'X-User-Role': role ?? 'USER',
      'Content-Type': 'application/json',
    };
    dev.log('[API_CLIENT] notifServiceHeaders — X-User-Id=$userId  X-User-Role=$role', name: 'ApiClient');
    return headers;
  }

  Future<Map<String, String>> roomServiceHeaders() async {
    final userId = await _authStorage.getUserId();
    final role = await _authStorage.getUserRole();
    final headers = {
      'X-User-Id': userId ?? '',
      'X-User-Role': role ?? 'USER',
      'Content-Type': 'application/json',
    };
    dev.log('[API_CLIENT] roomServiceHeaders — X-User-Id=$userId  X-User-Role=$role', name: 'ApiClient');
    return headers;
  }

  Future<Map<String, String>> workspaceServiceHeaders() async {
    final userId = await _authStorage.getUserId();
    final role = await _authStorage.getUserRole();
    return {
      'X-User-Id': userId ?? '',
      'X-User-Role': role ?? 'USER',
      'Content-Type': 'application/json',
    };
  }
}
