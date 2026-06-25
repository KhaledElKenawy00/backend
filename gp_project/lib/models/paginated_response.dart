class PaginatedResponse<T> {
  final List<T> items;
  final int page;
  final int size;
  final int total;

  PaginatedResponse({
    required this.items,
    required this.page,
    required this.size,
    required this.total,
  });

  factory PaginatedResponse.fromJson(
    Map<String, dynamic> json,
    T Function(Map<String, dynamic>) fromJson,
  ) {
    final rawItems = json['items'] ?? json['content'] ?? json['data'] ?? [];
    final items = (rawItems as List)
        .map((e) => fromJson(e as Map<String, dynamic>))
        .toList();

    return PaginatedResponse<T>(
      items: items,
      page: json['page'] as int? ?? 1,
      size: json['size'] as int? ?? items.length,
      total: json['total'] as int? ?? items.length,
    );
  }
}
