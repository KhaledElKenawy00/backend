class WorkspaceModel {
  final int id;
  final String name;
  final String slug;
  final int ownerId;
  final String? description;
  final String? logoUrl;
  final String status;
  final String? inviteToken;
  final String? defaultTimezone;
  final String? createdAt;

  WorkspaceModel({
    required this.id,
    required this.name,
    required this.slug,
    required this.ownerId,
    this.description,
    this.logoUrl,
    required this.status,
    this.inviteToken,
    this.defaultTimezone,
    this.createdAt,
  });

  factory WorkspaceModel.fromJson(Map<String, dynamic> j) => WorkspaceModel(
        id: (j['id'] as num).toInt(),
        name: j['name'] as String? ?? '',
        slug: j['slug'] as String? ?? '',
        ownerId: (j['ownerId'] as num?)?.toInt() ?? 0,
        description: j['description'] as String?,
        logoUrl: j['logoUrl'] as String?,
        status: j['status'] as String? ?? 'ACTIVE',
        inviteToken: j['inviteToken'] as String?,
        defaultTimezone: j['defaultTimezone'] as String?,
        createdAt: j['createdAt'] as String?,
      );
}

class DeskModel {
  final int id;
  final int userId;
  final int workspaceId;
  final String fullName;
  final String? nickName;
  final String? title;
  final String? workEmail;
  final String? phone;
  final String? personalImageUrl;
  final String? avatarCharacter;
  final String? timezone;
  final String status;
  final String? statusEmoji;
  final String? statusCustomText;
  final int? positionX;
  final int? positionY;
  final bool isOnline;
  final String? lastSeenAt;
  final String role;
  final String? bio;
  final int? teamId;
  final String inviteStatus;
  final bool isActive;
  final String? joinedAt;
  final List<String> links;

  DeskModel({
    required this.id,
    required this.userId,
    required this.workspaceId,
    required this.fullName,
    this.nickName,
    this.title,
    this.workEmail,
    this.phone,
    this.personalImageUrl,
    this.avatarCharacter,
    this.timezone,
    required this.status,
    this.statusEmoji,
    this.statusCustomText,
    this.positionX,
    this.positionY,
    required this.isOnline,
    this.lastSeenAt,
    required this.role,
    this.bio,
    this.teamId,
    required this.inviteStatus,
    required this.isActive,
    this.joinedAt,
    required this.links,
  });

  factory DeskModel.fromJson(Map<String, dynamic> j) => DeskModel(
        id: (j['id'] as num).toInt(),
        userId: (j['userId'] as num?)?.toInt() ?? 0,
        workspaceId: (j['workspaceId'] as num?)?.toInt() ?? 0,
        fullName: j['fullName'] as String? ?? '',
        nickName: j['nickName'] as String?,
        title: j['title'] as String?,
        workEmail: j['workEmail'] as String?,
        phone: j['phone'] as String?,
        personalImageUrl: j['personalImageUrl'] as String?,
        avatarCharacter: j['avatarCharacter'] as String?,
        timezone: j['timezone'] as String?,
        status: j['status'] as String? ?? 'ACTIVE',
        statusEmoji: j['statusEmoji'] as String?,
        statusCustomText: j['statusCustomText'] as String?,
        positionX: (j['positionX'] as num?)?.toInt(),
        positionY: (j['positionY'] as num?)?.toInt(),
        isOnline: j['isOnline'] as bool? ?? false,
        lastSeenAt: j['lastSeenAt'] as String?,
        role: j['role'] as String? ?? 'MEMBER',
        bio: j['bio'] as String?,
        teamId: (j['teamId'] as num?)?.toInt(),
        inviteStatus: j['inviteStatus'] as String? ?? 'ACCEPTED',
        isActive: j['isActive'] as bool? ?? true,
        joinedAt: j['joinedAt'] as String?,
        links: (j['links'] as List<dynamic>?)?.cast<String>() ?? [],
      );

  DeskModel copyWith({
    String? status,
    String? statusEmoji,
    String? statusCustomText,
    String? fullName,
    String? nickName,
    String? title,
    String? bio,
    int? positionX,
    int? positionY,
  }) =>
      DeskModel(
        id: id,
        userId: userId,
        workspaceId: workspaceId,
        fullName: fullName ?? this.fullName,
        nickName: nickName ?? this.nickName,
        title: title ?? this.title,
        workEmail: workEmail,
        phone: phone,
        personalImageUrl: personalImageUrl,
        avatarCharacter: avatarCharacter,
        timezone: timezone,
        status: status ?? this.status,
        statusEmoji: statusEmoji ?? this.statusEmoji,
        statusCustomText: statusCustomText ?? this.statusCustomText,
        positionX: positionX ?? this.positionX,
        positionY: positionY ?? this.positionY,
        isOnline: isOnline,
        lastSeenAt: lastSeenAt,
        role: role,
        bio: bio ?? this.bio,
        teamId: teamId,
        inviteStatus: inviteStatus,
        isActive: isActive,
        joinedAt: joinedAt,
        links: links,
      );
}

class TeamModel {
  final int id;
  final int workspaceId;
  final String name;
  final String? description;
  final String? createdAt;

  TeamModel({
    required this.id,
    required this.workspaceId,
    required this.name,
    this.description,
    this.createdAt,
  });

  factory TeamModel.fromJson(Map<String, dynamic> j) => TeamModel(
        id: (j['id'] as num).toInt(),
        workspaceId: (j['workspaceId'] as num?)?.toInt() ?? 0,
        name: j['name'] as String? ?? '',
        description: j['description'] as String?,
        createdAt: j['createdAt'] as String?,
      );
}

class InvitationModel {
  final int id;
  final int workspaceId;
  final String invitedEmail;
  final int invitedBy;
  final String? token;
  final String role;
  final String status;
  final String? expiresAt;
  final String? createdAt;

  InvitationModel({
    required this.id,
    required this.workspaceId,
    required this.invitedEmail,
    required this.invitedBy,
    this.token,
    required this.role,
    required this.status,
    this.expiresAt,
    this.createdAt,
  });

  factory InvitationModel.fromJson(Map<String, dynamic> j) => InvitationModel(
        id: (j['id'] as num).toInt(),
        workspaceId: (j['workspaceId'] as num?)?.toInt() ?? 0,
        invitedEmail: j['invitedEmail'] as String? ?? '',
        invitedBy: (j['invitedBy'] as num?)?.toInt() ?? 0,
        token: j['token'] as String?,
        role: j['role'] as String? ?? 'MEMBER',
        status: j['status'] as String? ?? 'PENDING',
        expiresAt: j['expiresAt'] as String?,
        createdAt: j['createdAt'] as String?,
      );
}
