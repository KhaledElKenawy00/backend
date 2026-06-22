import 'dart:convert';
import 'dart:developer' as dev;
import 'package:http/http.dart' as http;
import '../core/constants/api_constants.dart';

class AuthResponse {
  final String? token;
  final int? id;
  final String? email;
  final String? firstName;
  final String? lastName;
  final String? role;
  final String? errorMessage;

  AuthResponse({
    this.token,
    this.id,
    this.email,
    this.firstName,
    this.lastName,
    this.role,
    this.errorMessage,
  });

  factory AuthResponse.fromJson(Map<String, dynamic> json) {
    return AuthResponse(
      token: json['token'] as String?,
      id: json['id'] as int?,
      email: json['email'] as String?,
      firstName: json['firstName'] as String?,
      lastName: json['lastName'] as String?,
      role: json['role'] as String?,
      errorMessage: json['errorMessage'] as String?,
    );
  }
}

// "None" = backend's way of saying "no error" — treat it as success
bool _isBackendError(String? msg) =>
    msg != null && msg.isNotEmpty && msg != 'None';

String _extractMessage(String body) {
  try {
    final json = jsonDecode(body) as Map<String, dynamic>;
    return json['message'] as String? ??
        json['status'] as String? ??
        json['error'] as String? ??
        body;
  } catch (_) {
    return body;
  }
}

class AuthService {
  Future<AuthResponse> login(String email, String password) async {
    final uri = Uri.parse('${ApiConstants.userServiceBase}/api/auth/login');
    dev.log('[AUTH] POST $uri', name: 'AuthService');

    late http.Response response;
    try {
      response = await http.post(
        uri,
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({'email': email, 'password': password}),
      );
    } catch (e) {
      dev.log('[AUTH] Network error: $e', name: 'AuthService');
      throw Exception('لا يوجد اتصال بالسيرفر — تأكد من أنك على نفس الـ WiFi');
    }

    dev.log('[AUTH] status=${response.statusCode} body=${response.body}', name: 'AuthService');

    if (response.statusCode == 200 || response.statusCode == 201) {
      final json = jsonDecode(response.body) as Map<String, dynamic>;
      final authResponse = AuthResponse.fromJson(json);
      if (_isBackendError(authResponse.errorMessage)) {
        dev.log('[AUTH] Backend error: ${authResponse.errorMessage}', name: 'AuthService');
        throw Exception(authResponse.errorMessage);
      }
      if (authResponse.token == null || authResponse.token!.isEmpty) {
        throw Exception('لم يتم استلام الـ token من السيرفر');
      }
      dev.log('[AUTH] Login success — email=${authResponse.email}', name: 'AuthService');
      return authResponse;
    } else {
      String errorMsg = 'فشل تسجيل الدخول (${response.statusCode})';
      try {
        final json = jsonDecode(response.body) as Map<String, dynamic>;
        final backendMsg = json['errorMessage'] as String? ?? json['message'] as String?;
        if (_isBackendError(backendMsg)) errorMsg = backendMsg!;
      } catch (_) {}
      dev.log('[AUTH] Login failed: $errorMsg', name: 'AuthService');
      throw Exception(errorMsg);
    }
  }

  Future<AuthResponse> register({
    required String firstName,
    required String lastName,
    required String email,
    required String phoneNumber,
    required String password,
  }) async {
    final uri = Uri.parse('${ApiConstants.userServiceBase}/api/auth/register');
    dev.log('[AUTH] POST $uri — email=$email', name: 'AuthService');

    late http.Response response;
    try {
      response = await http.post(
        uri,
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'firstName': firstName,
          'lastName': lastName,
          'email': email,
          'phoneNumber': phoneNumber,
          'password': password,
        }),
      );
    } catch (e) {
      dev.log('[AUTH] Network error: $e', name: 'AuthService');
      throw Exception('لا يوجد اتصال بالسيرفر — تأكد من أنك على نفس الـ WiFi');
    }

    dev.log('[AUTH] status=${response.statusCode} body=${response.body}', name: 'AuthService');

    if (response.statusCode == 200 || response.statusCode == 201) {
      final json = jsonDecode(response.body) as Map<String, dynamic>;
      final authResponse = AuthResponse.fromJson(json);
      if (_isBackendError(authResponse.errorMessage)) {
        dev.log('[AUTH] Backend error: ${authResponse.errorMessage}', name: 'AuthService');
        throw Exception(authResponse.errorMessage);
      }
      if (authResponse.token == null || authResponse.token!.isEmpty) {
        throw Exception('لم يتم استلام الـ token من السيرفر');
      }
      dev.log('[AUTH] Register success — email=${authResponse.email}', name: 'AuthService');
      return authResponse;
    } else {
      String errorMsg = 'فشل إنشاء الحساب (${response.statusCode})';
      try {
        final json = jsonDecode(response.body) as Map<String, dynamic>;
        final backendMsg = json['errorMessage'] as String? ?? json['message'] as String?;
        if (_isBackendError(backendMsg)) errorMsg = backendMsg!;
      } catch (_) {}
      dev.log('[AUTH] Register failed: $errorMsg', name: 'AuthService');
      throw Exception(errorMsg);
    }
  }

  Future<String> requestOtp(String email) async {
    final uri = Uri.parse('${ApiConstants.userServiceBase}/api/auth/otp/request');
    dev.log('[AUTH] POST $uri — email=$email', name: 'AuthService');
    final response = await http.post(
      uri,
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({'email': email}),
    );
    final msg = _extractMessage(response.body);
    if (response.statusCode == 200 || response.statusCode == 201) return msg;
    throw Exception(msg);
  }

  Future<String> verifyOtp(String email, String otp) async {
    final uri = Uri.parse('${ApiConstants.userServiceBase}/api/auth/otp/verify');
    dev.log('[AUTH] POST $uri — email=$email', name: 'AuthService');
    final response = await http.post(
      uri,
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({'email': email, 'otp': otp}),
    );
    final msg = _extractMessage(response.body);
    if (response.statusCode == 200 || response.statusCode == 201) return msg;
    throw Exception(msg);
  }
}
