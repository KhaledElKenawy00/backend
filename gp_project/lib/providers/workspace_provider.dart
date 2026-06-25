import 'package:flutter/foundation.dart';
import '../models/workspace_model.dart';
import '../services/workspace_service.dart';

class WorkspaceProvider extends ChangeNotifier {
  final WorkspaceService _service;

  WorkspaceProvider(this._service);

  List<WorkspaceModel> _workspaces = [];
  WorkspaceModel? _activeWorkspace;
  DeskModel? _myDesk;
  List<DeskModel> _desks = [];
  List<TeamModel> _teams = [];
  List<InvitationModel> _invitations = [];

  bool _loading = false;
  String? _error;

  List<WorkspaceModel> get workspaces => _workspaces;
  WorkspaceModel? get activeWorkspace => _activeWorkspace;
  int? get activeWorkspaceId => _activeWorkspace?.id;
  DeskModel? get myDesk => _myDesk;
  List<DeskModel> get desks => _desks;
  List<TeamModel> get teams => _teams;
  List<InvitationModel> get invitations => _invitations;
  bool get isLoading => _loading;
  String? get error => _error;

  // ── Workspaces ─────────────────────────────────────────────────────────────

  Future<void> fetchMyWorkspaces() async {
    _loading = true;
    _error = null;
    notifyListeners();
    try {
      _workspaces = await _service.getMyWorkspaces();
      if (_activeWorkspace == null && _workspaces.isNotEmpty) {
        _activeWorkspace = _workspaces.first;
      }
    } catch (e) {
      _error = e.toString().replaceFirst('Exception: ', '');
    } finally {
      _loading = false;
      notifyListeners();
    }
  }

  Future<WorkspaceModel> createWorkspace({
    required String name,
    required String slug,
    String? description,
    String defaultTimezone = 'UTC',
  }) async {
    final ws = await _service.createWorkspace(
      name: name,
      slug: slug,
      description: description,
      defaultTimezone: defaultTimezone,
    );
    _workspaces = [ws, ..._workspaces];
    _activeWorkspace ??= ws;
    notifyListeners();
    return ws;
  }

  void selectWorkspace(WorkspaceModel ws) {
    _activeWorkspace = ws;
    _myDesk = null;
    _desks = [];
    _teams = [];
    _invitations = [];
    notifyListeners();
  }

  // ── Desks ──────────────────────────────────────────────────────────────────

  Future<void> fetchDesks(int workspaceId) async {
    _loading = true;
    _error = null;
    notifyListeners();
    try {
      _desks = await _service.getDesks(workspaceId);
    } catch (e) {
      _error = e.toString().replaceFirst('Exception: ', '');
    } finally {
      _loading = false;
      notifyListeners();
    }
  }

  Future<void> fetchMyDesk(int workspaceId) async {
    try {
      _myDesk = await _service.getMyDesk(workspaceId);
      notifyListeners();
    } catch (e) {
      // myDesk can be null if user has no desk yet
    }
  }

  Future<void> updateMyDesk(int workspaceId, int deskId, {
    String? fullName,
    String? nickName,
    String? title,
    String? bio,
    int? teamId,
  }) async {
    final updated = await _service.updateMyDesk(workspaceId, deskId,
        fullName: fullName, nickName: nickName, title: title, bio: bio, teamId: teamId);
    _myDesk = updated;
    final idx = _desks.indexWhere((d) => d.id == deskId);
    if (idx != -1) {
      final list = List<DeskModel>.from(_desks);
      list[idx] = updated;
      _desks = list;
    }
    notifyListeners();
  }

  Future<void> updateMyDeskStatus(int workspaceId, int deskId, {
    required String status,
    String? statusEmoji,
    String? statusCustomText,
  }) async {
    final updated = await _service.updateDeskStatus(workspaceId, deskId,
        status: status, statusEmoji: statusEmoji, statusCustomText: statusCustomText);
    _myDesk = updated;
    final idx = _desks.indexWhere((d) => d.id == deskId);
    if (idx != -1) {
      final list = List<DeskModel>.from(_desks);
      list[idx] = updated;
      _desks = list;
    }
    notifyListeners();
  }

  // ── Teams ──────────────────────────────────────────────────────────────────

  Future<void> fetchTeams(int workspaceId) async {
    _loading = true;
    _error = null;
    notifyListeners();
    try {
      _teams = await _service.getTeams(workspaceId);
    } catch (e) {
      _error = e.toString().replaceFirst('Exception: ', '');
    } finally {
      _loading = false;
      notifyListeners();
    }
  }

  Future<void> createTeam(int workspaceId, String name, {String? description}) async {
    final team = await _service.createTeam(workspaceId, name, description: description);
    _teams = [team, ..._teams];
    notifyListeners();
  }

  Future<void> updateTeam(int workspaceId, int teamId, {String? name, String? description}) async {
    final updated = await _service.updateTeam(workspaceId, teamId, name: name, description: description);
    _teams = _teams.map((t) => t.id == teamId ? updated : t).toList();
    notifyListeners();
  }

  Future<void> deleteTeam(int workspaceId, int teamId) async {
    await _service.deleteTeam(workspaceId, teamId);
    _teams = _teams.where((t) => t.id != teamId).toList();
    notifyListeners();
  }

  // ── Invitations ────────────────────────────────────────────────────────────

  Future<void> fetchInvitations(int workspaceId) async {
    _loading = true;
    _error = null;
    notifyListeners();
    try {
      _invitations = await _service.getInvitations(workspaceId);
    } catch (e) {
      _error = e.toString().replaceFirst('Exception: ', '');
    } finally {
      _loading = false;
      notifyListeners();
    }
  }

  Future<void> inviteMember(int workspaceId, String email, String role) async {
    final inv = await _service.inviteMember(workspaceId, email, role);
    _invitations = [inv, ..._invitations];
    notifyListeners();
  }

  Future<void> revokeInvitation(int workspaceId, int invitationId) async {
    await _service.revokeInvitation(workspaceId, invitationId);
    _invitations = _invitations.where((i) => i.id != invitationId).toList();
    notifyListeners();
  }

  Future<void> acceptInvitation(String token) async {
    await _service.acceptInvitation(token);
    await fetchMyWorkspaces();
  }

  Future<void> declineInvitation(String token) async {
    await _service.declineInvitation(token);
  }
}
