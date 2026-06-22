class ChannelModel {
  final String id;
  final String name;
  final String type; // "GROUP" or "DM"
  final int? workspaceId;
  final List<int> members;
  final int? createdBy;
  final String? createdAt;
  final String? updatedAt;

  ChannelModel({
    required this.id,
    required this.name,
    required this.type,
    this.workspaceId,
    this.members = const [],
    this.createdBy,
    this.createdAt,
    this.updatedAt,
  });

  factory ChannelModel.fromJson(Map<String, dynamic> json) {
    final rawMembers = json['members'];
    List<int> membersList = [];
    if (rawMembers is List) {
      membersList = rawMembers.map((e) => e as int).toList();
    }
    return ChannelModel(
      id: json['id']?.toString() ?? '',
      name: json['name'] as String? ?? '',
      type: json['type'] as String? ?? 'GROUP',
      workspaceId: json['workspaceId'] as int?,
      members: membersList,
      createdBy: json['createdBy'] as int?,
      createdAt: json['createdAt'] as String?,
      updatedAt: json['updatedAt'] as String?,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'name': name,
      'type': type,
      'workspaceId': workspaceId,
      'members': members,
      'createdBy': createdBy,
      'createdAt': createdAt,
      'updatedAt': updatedAt,
    };
  }

  bool get isDm => type == 'DM' || type == 'DIRECT';

  String displayName(int? currentUserId, {Map<int, String>? userNames}) {
    if (isDm) {
      if (currentUserId != null) {
        final otherId = members.firstWhere(
          (m) => m != currentUserId,
          orElse: () => members.isNotEmpty ? members.first : 0,
        );
        return userNames?[otherId] ?? 'User $otherId';
      }
      // currentUserId not yet loaded — show any available member name
      for (final m in members) {
        final n = userNames?[m];
        if (n != null) return n;
      }
      return members.isNotEmpty ? 'User ${members.first}' : 'Direct Message';
    }
    return name.isEmpty ? 'Channel' : name;
  }

  int otherMemberId(int currentUserId) {
    return members.firstWhere(
      (m) => m != currentUserId,
      orElse: () => members.isNotEmpty ? members.first : 0,
    );
  }
}
