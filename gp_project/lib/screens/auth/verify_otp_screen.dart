import 'package:flutter/material.dart';
import '../../services/auth_service.dart';

class VerifyOtpScreen extends StatefulWidget {
  final String email;
  const VerifyOtpScreen({super.key, required this.email});

  @override
  State<VerifyOtpScreen> createState() => _VerifyOtpScreenState();
}

class _VerifyOtpScreenState extends State<VerifyOtpScreen> {
  final _otpController = TextEditingController();
  bool _loading = false;

  @override
  void dispose() {
    _otpController.dispose();
    super.dispose();
  }

  Future<void> _verify() async {
    final otp = _otpController.text.trim();
    if (otp.length < 4) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Enter the full OTP code')));
      return;
    }
    setState(() => _loading = true);
    try {
      final message = await AuthService().verifyOtp(widget.email, otp);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(message), backgroundColor: Colors.green));
      Navigator.of(context).popUntil((r) => r.isFirst);
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(e.toString().replaceFirst('Exception: ', '')),
            backgroundColor: Colors.red));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _resend() async {
    try {
      await AuthService().requestOtp(widget.email);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('OTP resent!')));
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(e.toString().replaceFirst('Exception: ', '')),
            backgroundColor: Colors.red));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Verify OTP')),
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Icon(Icons.mark_email_read_outlined, size: 64,
                    color: Theme.of(context).colorScheme.primary),
                const SizedBox(height: 16),
                Text('Check your email',
                    textAlign: TextAlign.center,
                    style: Theme.of(context).textTheme.headlineSmall),
                const SizedBox(height: 8),
                Text('We sent a code to ${widget.email}',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                        color: Theme.of(context)
                            .colorScheme
                            .onSurface
                            .withValues(alpha: 0.6))),
                const SizedBox(height: 32),
                TextField(
                  controller: _otpController,
                  keyboardType: TextInputType.number,
                  textAlign: TextAlign.center,
                  maxLength: 10,
                  style: const TextStyle(fontSize: 28, letterSpacing: 8),
                  decoration: const InputDecoration(
                    labelText: 'OTP Code',
                    border: OutlineInputBorder(),
                    counterText: '',
                  ),
                ),
                const SizedBox(height: 24),
                FilledButton(
                  onPressed: _loading ? null : _verify,
                  style: FilledButton.styleFrom(
                      padding: const EdgeInsets.symmetric(vertical: 14)),
                  child: _loading
                      ? const SizedBox(
                          height: 20, width: 20,
                          child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                      : const Text('Verify', style: TextStyle(fontSize: 16)),
                ),
                const SizedBox(height: 12),
                TextButton(
                  onPressed: _resend,
                  child: const Text('Resend OTP'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
