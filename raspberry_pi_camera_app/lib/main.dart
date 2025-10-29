import 'package:flutter/material.dart';

void main() {
  runApp(MyApp());
}

class MyApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: CameraStreamPage(),
    );
  }
}

class CameraStreamPage extends StatefulWidget {
  @override
  _CameraStreamPageState createState() => _CameraStreamPageState();
}

class _CameraStreamPageState extends State<CameraStreamPage> {
  bool _isConnected = false;
  String _monitorText = 'Waiting for Connection';

  void _connectToRaspberryPi() {
    setState(() {
      _isConnected = true;
      _monitorText = 'Connected to Raspberry Pi';
    });
  }

  void _disconnectFromRaspberryPi() {
    setState(() {
      _isConnected = false;
      _monitorText = 'Disconnected from Raspberry Pi';
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('Raspberry Pi Camera App'),
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            // Monitor Display
            Container(
              width: 300,
              height: 200,
              decoration: BoxDecoration(
                border: Border.all(color: Colors.black),
                color: _isConnected ? Colors.green[100] : Colors.red[100],
              ),
              child: Center(
                child: Text(
                  _monitorText,
                  style: TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            ),
            SizedBox(height: 30),

            // Button Row
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                ElevatedButton(
                  onPressed: _connectToRaspberryPi,
                  child: Text('Connect'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.green,
                  ),
                ),
                SizedBox(width: 20),
                ElevatedButton(
                  onPressed: _disconnectFromRaspberryPi,
                  child: Text('Disconnect'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.red,
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}