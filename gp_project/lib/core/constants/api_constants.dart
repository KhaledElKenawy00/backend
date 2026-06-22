class ApiConstants {
  static const String _host = '192.168.1.3';

  static const String userServiceBase = 'http://$_host:8091';
  static const String chatServiceBase = 'http://$_host:8084';
  static const String notifServiceBase = 'http://$_host:8082';
  static const String roomServiceBase = 'http://$_host:8086';
  static const String chatWsUrl = 'ws://$_host:8084/api/chat/connect';
  static const String notifWsUrl = 'ws://$_host:8082/ws/notifications';
  static const String roomWsUrl = 'ws://$_host:8086/ws/rooms';
}
