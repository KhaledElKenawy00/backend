import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class AuthStorage {
  static const _storage = FlutterSecureStorage();

  static const _keyToken = 'token';
  static const _keyUserId = 'userId';
  static const _keyFirstName = 'firstName';
  static const _keyLastName = 'lastName';
  static const _keyEmail = 'email';
  static const _keyUserRole = 'userRole';

  Future<void> saveAuthData({
    required String token,
    required String userId,
    required String firstName,
    required String lastName,
    required String email,
    String userRole = 'USER',
  }) async {
    await Future.wait([
      _storage.write(key: _keyToken, value: token),
      _storage.write(key: _keyUserId, value: userId),
      _storage.write(key: _keyFirstName, value: firstName),
      _storage.write(key: _keyLastName, value: lastName),
      _storage.write(key: _keyEmail, value: email),
      _storage.write(key: _keyUserRole, value: userRole),
    ]);
  }

  Future<String?> getToken() => _storage.read(key: _keyToken);
  Future<String?> getUserId() => _storage.read(key: _keyUserId);
  Future<String?> getFirstName() => _storage.read(key: _keyFirstName);
  Future<String?> getLastName() => _storage.read(key: _keyLastName);
  Future<String?> getEmail() => _storage.read(key: _keyEmail);
  Future<String?> getUserRole() async {
    final role = await _storage.read(key: _keyUserRole);
    return role ?? 'USER';
  }

  Future<bool> hasToken() async {
    final token = await _storage.read(key: _keyToken);
    return token != null && token.isNotEmpty;
  }

  Future<void> clear() async {
    await _storage.deleteAll();
  }
}
