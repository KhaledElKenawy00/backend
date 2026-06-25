import 'dart:convert';
import 'dart:developer' as dev;
import 'dart:typed_data';
import 'package:http/http.dart' as http;
import 'package:http_parser/http_parser.dart';
import '../core/constants/api_constants.dart';
import '../core/network/api_client.dart';
import '../models/user_model.dart';

class UserService {
  final ApiClient _apiClient;

  UserService(this._apiClient);

  Future<UserModel> getMe() async {
    final headers = await _apiClient.userServiceHeaders();
    final uri = Uri.parse('${ApiConstants.userServiceBase}/api/users/me');
    dev.log('[USER] GET $uri', name: 'UserService');

    late http.Response response;
    try {
      response = await http.get(uri, headers: headers);
    } catch (e) {
      dev.log('[USER] Network error: $e', name: 'UserService');
      throw Exception('لا يوجد اتصال بالسيرفر');
    }

    dev.log('[USER] status=${response.statusCode} body=${response.body}', name: 'UserService');

    if (response.statusCode == 200) {
      final json = jsonDecode(response.body) as Map<String, dynamic>;
      final user = UserModel.fromJson(json);
      dev.log('[USER] getMe success — id=${user.id} email=${user.email}', name: 'UserService');
      return user;
    } else {
      throw Exception('فشل تحميل بيانات المستخدم (${response.statusCode})');
    }
  }

  Future<void> changePassword(String oldPassword, String newPassword) async {
    final headers = await _apiClient.userServiceHeaders();
    final uri =
        Uri.parse('${ApiConstants.userServiceBase}/api/users/me/password');
    final response = await http.put(
      uri,
      headers: headers,
      body: jsonEncode({'oldPassword': oldPassword, 'newPassword': newPassword}),
    );

    if (response.statusCode != 200 && response.statusCode != 204) {
      String errorMsg = 'Failed to change password (${response.statusCode})';
      try {
        final json = jsonDecode(response.body) as Map<String, dynamic>;
        errorMsg = json['message'] as String? ?? json['error'] as String? ?? errorMsg;
      } catch (_) {}
      throw Exception(errorMsg);
    }
  }

  // filePath = XFile.path from image_picker
  Future<void> uploadPhoto(String filePath) async {
    final headers = await _apiClient.userServiceHeaders();
    final uri = Uri.parse('${ApiConstants.userServiceBase}/api/users/me/photo');
    dev.log('[USER] POST $uri — file=$filePath', name: 'UserService');

    final contentType = _mimeFromPath(filePath);
    dev.log('[USER] upload contentType=$contentType', name: 'UserService');

    final request = http.MultipartRequest('POST', uri)
      ..headers['Authorization'] = headers['Authorization'] ?? '';

    request.files.add(await http.MultipartFile.fromPath(
      'file',
      filePath,
      contentType: MediaType.parse(contentType),
    ));

    late http.StreamedResponse streamedResponse;
    try {
      streamedResponse = await request.send();
    } catch (e) {
      dev.log('[USER] upload network error: $e', name: 'UserService');
      throw Exception('لا يوجد اتصال بالسيرفر');
    }

    final body = await streamedResponse.stream.bytesToString();
    dev.log('[USER] upload status=${streamedResponse.statusCode} body=$body', name: 'UserService');

    if (streamedResponse.statusCode != 200 && streamedResponse.statusCode != 201) {
      String msg = 'فشل رفع الصورة (${streamedResponse.statusCode})';
      try {
        final json = jsonDecode(body) as Map<String, dynamic>;
        msg = json['Error'] as String? ?? json['message'] as String? ?? msg;
      } catch (_) {}
      throw Exception(msg);
    }
  }

  Future<List<UserModel>> getUsers() async {
    final headers = await _apiClient.userServiceHeaders();
    final uri = Uri.parse('${ApiConstants.userServiceBase}/api/users');
    dev.log('[USER] GET $uri', name: 'UserService');

    late http.Response response;
    try {
      response = await http.get(uri, headers: headers);
    } catch (e) {
      throw Exception('لا يوجد اتصال بالسيرفر');
    }

    dev.log('[USER] getAllUsers status=${response.statusCode}', name: 'UserService');

    if (response.statusCode == 200) {
      final list = jsonDecode(response.body) as List<dynamic>;
      return list
          .map((e) => UserModel.fromJson(e as Map<String, dynamic>))
          .toList();
    } else {
      throw Exception('فشل تحميل المستخدمين (${response.statusCode})');
    }
  }

  static String _mimeFromPath(String path) {
    final ext = path.split('.').last.toLowerCase();
    return switch (ext) {
      'jpg' || 'jpeg' => 'image/jpeg',
      'png'           => 'image/png',
      'gif'           => 'image/gif',
      'webp'          => 'image/webp',
      'heic'          => 'image/heic',
      'bmp'           => 'image/bmp',
      _               => 'image/jpeg',
    };
  }

  Future<Uint8List?> getPhoto() async {
    final headers = await _apiClient.userServiceHeaders();
    final uri =
        Uri.parse('${ApiConstants.userServiceBase}/api/users/me/photo');
    final response = await http.get(uri, headers: headers);

    if (response.statusCode == 200) {
      return response.bodyBytes;
    }
    return null;
  }
}
