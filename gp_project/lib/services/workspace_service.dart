import 'dart:convert';
import 'dart:developer' as dev;
import 'package:http/http.dart' as http;
import '../core/constants/api_constants.dart';
import '../core/network/api_client.dart';
import '../models/workspace_model.dart';

class WorkspaceService {
  final ApiClient _apiClient;

  WorkspaceService(this._apiClient);

  // ── Workspace ─────────────────────────────────────────────────────────────

  Future<List<WorkspaceModel>> getMyWorkspaces() async {
    final headers = await _apiClient.workspaceServiceHeaders();
    final uri = Uri.parse('${ApiConstants.workspaceServiceBase}/api/workspace/mine');
    dev.log('[WORKSPACE] GET $uri', name: 'WorkspaceService');
    final response = await http.get(uri, headers: headers);
    _check(response, 'fetch workspaces');
    final list = jsonDecode(response.body) as List<dynamic>;
    return list.map((e) => WorkspaceModel.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<WorkspaceModel> createWorkspace({
    required String name,
    required String slug,
    String? description,
    String defaultTimezone = 'UTC',
  }) async {
    final headers = await _apiClient.workspaceServiceHeaders();
    final uri = Uri.parse('${ApiConstants.workspaceServiceBase}/api/workspace');
    final response = await http.post(uri, headers: headers,
        body: jsonEncode({
          'name': name,
          'slug': slug,
          if (description != null) 'description': description,
          'defaultTimezone': defaultTimezone,
        }));
    _check(response, 'create workspace');
    return WorkspaceModel.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
  }

  Future<WorkspaceModel> getWorkspace(int id) async {
    final headers = await _apiClient.workspaceServiceHeaders();
    final uri = Uri.parse('${ApiConstants.workspaceServiceBase}/api/workspace/$id');
    final response = await http.get(uri, headers: headers);
    _check(response, 'get workspace');
    return WorkspaceModel.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
  }

  Future<WorkspaceModel> updateWorkspace(int id, {String? name, String? description}) async {
    final headers = await _apiClient.workspaceServiceHeaders();
    final uri = Uri.parse('${ApiConstants.workspaceServiceBase}/api/workspace/$id');
    final response = await http.put(uri, headers: headers,
        body: jsonEncode({
          if (name != null) 'name': name,
          if (description != null) 'description': description,
        }));
    _check(response, 'update workspace');
    return WorkspaceModel.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
  }

  Future<void> deleteWorkspace(int id) async {
    final headers = await _apiClient.workspaceServiceHeaders();
    final uri = Uri.parse('${ApiConstants.workspaceServiceBase}/api/workspace/$id');
    final response = await http.delete(uri, headers: headers);
    _check(response, 'delete workspace');
  }

  // ── Desks ─────────────────────────────────────────────────────────────────

  Future<List<DeskModel>> getDesks(int workspaceId) async {
    final headers = await _apiClient.workspaceServiceHeaders();
    final uri = Uri.parse(
        '${ApiConstants.workspaceServiceBase}/api/workspace/$workspaceId/desks');
    final response = await http.get(uri, headers: headers);
    _check(response, 'fetch desks');
    final list = jsonDecode(response.body) as List<dynamic>;
    return list.map((e) => DeskModel.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<DeskModel> getMyDesk(int workspaceId) async {
    final headers = await _apiClient.workspaceServiceHeaders();
    final uri = Uri.parse(
        '${ApiConstants.workspaceServiceBase}/api/workspace/$workspaceId/desks/me');
    final response = await http.get(uri, headers: headers);
    _check(response, 'fetch my desk');
    return DeskModel.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
  }

  Future<DeskModel> updateMyDesk(int workspaceId, int deskId, {
    String? fullName,
    String? nickName,
    String? title,
    String? bio,
    int? teamId,
  }) async {
    final headers = await _apiClient.workspaceServiceHeaders();
    final uri = Uri.parse(
        '${ApiConstants.workspaceServiceBase}/api/workspace/$workspaceId/desks/$deskId');
    final response = await http.put(uri, headers: headers,
        body: jsonEncode({
          if (fullName != null) 'fullName': fullName,
          if (nickName != null) 'nickName': nickName,
          if (title != null) 'title': title,
          if (bio != null) 'bio': bio,
          if (teamId != null) 'teamId': teamId,
        }));
    _check(response, 'update desk');
    return DeskModel.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
  }

  Future<DeskModel> updateDeskStatus(int workspaceId, int deskId, {
    required String status,
    String? statusEmoji,
    String? statusCustomText,
  }) async {
    final headers = await _apiClient.workspaceServiceHeaders();
    final uri = Uri.parse(
        '${ApiConstants.workspaceServiceBase}/api/workspace/$workspaceId/desks/$deskId/status');
    final response = await http.patch(uri, headers: headers,
        body: jsonEncode({
          'status': status,
          if (statusEmoji != null) 'statusEmoji': statusEmoji,
          if (statusCustomText != null) 'statusCustomText': statusCustomText,
        }));
    _check(response, 'update desk status');
    return DeskModel.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
  }

  // ── Teams ─────────────────────────────────────────────────────────────────

  Future<List<TeamModel>> getTeams(int workspaceId) async {
    final headers = await _apiClient.workspaceServiceHeaders();
    final uri = Uri.parse(
        '${ApiConstants.workspaceServiceBase}/api/workspace/$workspaceId/teams');
    final response = await http.get(uri, headers: headers);
    _check(response, 'fetch teams');
    final list = jsonDecode(response.body) as List<dynamic>;
    return list.map((e) => TeamModel.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<TeamModel> createTeam(int workspaceId, String name, {String? description}) async {
    final headers = await _apiClient.workspaceServiceHeaders();
    final uri = Uri.parse(
        '${ApiConstants.workspaceServiceBase}/api/workspace/$workspaceId/teams');
    final response = await http.post(uri, headers: headers,
        body: jsonEncode({
          'name': name,
          if (description != null) 'description': description,
        }));
    _check(response, 'create team');
    return TeamModel.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
  }

  Future<TeamModel> updateTeam(int workspaceId, int teamId, {String? name, String? description}) async {
    final headers = await _apiClient.workspaceServiceHeaders();
    final uri = Uri.parse(
        '${ApiConstants.workspaceServiceBase}/api/workspace/$workspaceId/teams/$teamId');
    final response = await http.put(uri, headers: headers,
        body: jsonEncode({
          if (name != null) 'name': name,
          if (description != null) 'description': description,
        }));
    _check(response, 'update team');
    return TeamModel.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
  }

  Future<void> deleteTeam(int workspaceId, int teamId) async {
    final headers = await _apiClient.workspaceServiceHeaders();
    final uri = Uri.parse(
        '${ApiConstants.workspaceServiceBase}/api/workspace/$workspaceId/teams/$teamId');
    final response = await http.delete(uri, headers: headers);
    _check(response, 'delete team');
  }

  // ── Invitations ───────────────────────────────────────────────────────────

  Future<List<InvitationModel>> getInvitations(int workspaceId) async {
    final headers = await _apiClient.workspaceServiceHeaders();
    final uri = Uri.parse(
        '${ApiConstants.workspaceServiceBase}/api/workspace/$workspaceId/invitations');
    final response = await http.get(uri, headers: headers);
    _check(response, 'fetch invitations');
    final list = jsonDecode(response.body) as List<dynamic>;
    return list.map((e) => InvitationModel.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<InvitationModel> inviteMember(int workspaceId, String email, String role) async {
    final headers = await _apiClient.workspaceServiceHeaders();
    final uri = Uri.parse(
        '${ApiConstants.workspaceServiceBase}/api/workspace/$workspaceId/invitations');
    final response = await http.post(uri, headers: headers,
        body: jsonEncode({'email': email, 'role': role}));
    _check(response, 'invite member');
    return InvitationModel.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
  }

  Future<void> revokeInvitation(int workspaceId, int invitationId) async {
    final headers = await _apiClient.workspaceServiceHeaders();
    final uri = Uri.parse(
        '${ApiConstants.workspaceServiceBase}/api/workspace/$workspaceId/invitations/$invitationId');
    final response = await http.delete(uri, headers: headers);
    _check(response, 'revoke invitation');
  }

  Future<void> acceptInvitation(String token) async {
    final headers = await _apiClient.workspaceServiceHeaders();
    final uri = Uri.parse('${ApiConstants.workspaceServiceBase}/api/invitations/accept');
    final response = await http.post(uri, headers: headers,
        body: jsonEncode({'token': token}));
    _check(response, 'accept invitation');
  }

  Future<void> declineInvitation(String token) async {
    final headers = await _apiClient.workspaceServiceHeaders();
    final uri = Uri.parse('${ApiConstants.workspaceServiceBase}/api/invitations/decline');
    final response = await http.post(uri, headers: headers,
        body: jsonEncode({'token': token}));
    _check(response, 'decline invitation');
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  void _check(http.Response r, String action) {
    if (r.statusCode >= 200 && r.statusCode < 300) return;
    String msg = 'Failed to $action (${r.statusCode})';
    try {
      final json = jsonDecode(r.body) as Map<String, dynamic>;
      msg = json['message'] as String? ?? json['error'] as String? ?? msg;
    } catch (_) {}
    dev.log('[WORKSPACE] $action failed ${r.statusCode}: ${r.body}', name: 'WorkspaceService');
    throw Exception(msg);
  }
}
