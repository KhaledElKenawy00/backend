class ThreadModel {
  final String id;
  final String channelId;
  final String rootMessageId;
  final String name;
  final int? createdBy;
  final bool deleted;
  final String? createdAt;
  final String? updatedAt;

  ThreadModel({
    required this.id,
    required this.channelId,
    this.rootMessageId = '',
    required this.name,
    this.createdBy,
    this.deleted = false,
    this.createdAt,
    this.updatedAt,
  });

  factory ThreadModel.fromJson(Map<String, dynamic> json) => ThreadModel(
        id: json['id']?.toString() ?? '',
        channelId: json['channelId']?.toString() ?? '',
        rootMessageId: json['rootMessageId']?.toString() ?? '',
        name: json['name'] as String? ?? '',
        createdBy: json['createdBy'] as int?,
        deleted: json['deleted'] as bool? ?? false,
        createdAt: json['createdAt'] as String?,
        updatedAt: json['updatedAt'] as String?,
      );
}
