import 'dart:developer' as dev;
import 'package:flutter/foundation.dart';
import '../core/storage/auth_storage.dart';
import '../core/network/api_client.dart';
import '../models/user_model.dart';
import '../services/auth_service.dart';
import '../services/user_service.dart';

class AuthProvider extends ChangeNotifier {
  final AuthStorage _authStorage;
  final AuthService _authService;
  late final ApiClient _apiClient;
  late final UserService _userService;

  UserModel? _currentUser;
  bool _isLoggedIn = false;
  bool _isLoading = false;
  String? _errorMessage;

  AuthProvider(this._authStorage, this._authService) {
    _apiClient = ApiClient(_authStorage);
    _userService = UserService(_apiClient);
  }

  UserModel? get currentUser => _currentUser;
  bool get isLoggedIn => _isLoggedIn;
  bool get isLoading => _isLoading;
  String? get errorMessage => _errorMessage;
  AuthStorage get authStorage => _authStorage;
  ApiClient get apiClient => _apiClient;

  Future<bool> checkLoginStatus() async {
    final hasToken = await _authStorage.hasToken();
    if (hasToken) {
      try {
        _currentUser = await _userService.getMe();
        _isLoggedIn = true;
        // Back-fill userId/role if storage holds stale "0" from pre-fix logins
        final storedId = await _authStorage.getUserId();
        if (storedId == null || storedId == '0') {
          dev.log('[AUTH_PROVIDER] back-filling stale userId in storage → ${_currentUser!.id}', name: 'AuthProvider');
          final token = await _authStorage.getToken();
          await _authStorage.saveAuthData(
            token: token!,
            userId: _currentUser!.id.toString(),
            firstName: _currentUser!.firstName,
            lastName: _currentUser!.lastName,
            email: _currentUser!.email,
            userRole: _currentUser!.role,
          );
        }
      } catch (_) {
        await _authStorage.clear();
        _isLoggedIn = false;
      }
    } else {
      _isLoggedIn = false;
    }
    notifyListeners();
    return _isLoggedIn;
  }

  Future<void> login(String email, String password) async {
    _isLoading = true;
    _errorMessage = null;
    notifyListeners();

    try {
      dev.log('[AUTH_PROVIDER] login start — email=$email', name: 'AuthProvider');
      final authResponse = await _authService.login(email, password);

      await _authStorage.saveAuthData(
        token: authResponse.token!,
        userId: authResponse.id?.toString() ?? '0',
        firstName: authResponse.firstName ?? '',
        lastName: authResponse.lastName ?? '',
        email: authResponse.email ?? email,
        userRole: authResponse.role ?? 'USER',
      );

      // fetch full profile for display (id/role already stored above)
      final userModel = await _userService.getMe();
      _currentUser = userModel;
      _isLoggedIn = true;
    } catch (e) {
      _errorMessage = e.toString().replaceFirst('Exception: ', '');
      dev.log('[AUTH_PROVIDER] login error: $_errorMessage', name: 'AuthProvider');
      _isLoggedIn = false;
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<void> register({
    required String firstName,
    required String lastName,
    required String email,
    required String phoneNumber,
    required String password,
  }) async {
    _isLoading = true;
    _errorMessage = null;
    notifyListeners();

    try {
      dev.log('[AUTH_PROVIDER] register start — email=$email', name: 'AuthProvider');
      final authResponse = await _authService.register(
        firstName: firstName,
        lastName: lastName,
        email: email,
        phoneNumber: phoneNumber,
        password: password,
      );

      await _authStorage.saveAuthData(
        token: authResponse.token!,
        userId: authResponse.id?.toString() ?? '0',
        firstName: authResponse.firstName ?? firstName,
        lastName: authResponse.lastName ?? lastName,
        email: authResponse.email ?? email,
        userRole: authResponse.role ?? 'USER',
      );

      // fetch full profile for display
      final userModel = await _userService.getMe();
      _currentUser = userModel;
      _isLoggedIn = true;
    } catch (e) {
      _errorMessage = e.toString().replaceFirst('Exception: ', '');
      dev.log('[AUTH_PROVIDER] register error: $_errorMessage', name: 'AuthProvider');
      _isLoggedIn = false;
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<void> refreshProfile() async {
    try {
      _currentUser = await _userService.getMe();
      notifyListeners();
    } catch (_) {}
  }

  Future<void> logout() async {
    await _authStorage.clear();
    _currentUser = null;
    _isLoggedIn = false;
    _errorMessage = null;
    notifyListeners();
  }

  void clearError() {
    _errorMessage = null;
    notifyListeners();
  }
}
