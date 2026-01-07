/**
 * Robot Control WebSocket Client
 */
class RobotController {
    constructor() {
        this.ws = null;
        this.isConnected = false;
        this.speedMultiplier = 1.0;
        this.currentAction = 'none';
        this.pressedKeys = new Set();
        this.cameraActive = false;
        this.cameraUrl = 'http://192.168.0.3:8080'; // web_video_server URL

        this.init();
    }

    init() {
        this.setupWebSocket();
        this.setupEventListeners();
        this.setupKeyboardControls();
        this.setupCameraControls();
    }

    setupWebSocket() {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = `${protocol}//${window.location.host}/ws/robot`;

        this.log(`Connecting to ${wsUrl}...`);

        this.ws = new WebSocket(wsUrl);

        this.ws.onopen = () => {
            this.isConnected = true;
            this.updateConnectionStatus(true);
            this.log('Connected to robot controller');
        };

        this.ws.onmessage = (event) => {
            this.handleMessage(JSON.parse(event.data));
        };

        this.ws.onclose = () => {
            this.isConnected = false;
            this.updateConnectionStatus(false);
            this.log('Disconnected from robot controller');

            // Attempt to reconnect after 3 seconds
            setTimeout(() => {
                this.log('Attempting to reconnect...');
                this.setupWebSocket();
            }, 3000);
        };

        this.ws.onerror = (error) => {
            this.log('WebSocket error: ' + error.message);
        };
    }

    handleMessage(data) {
        switch (data.type) {
            case 'connected':
                this.log(data.message);
                if (data.speedMultiplier) {
                    this.updateSpeed(data.speedMultiplier);
                }
                break;

            case 'command_ack':
                this.updateRobotState(data);
                this.log(`Command: ${data.action} - X:${data.linearX.toFixed(2)} Y:${data.linearY.toFixed(2)} Z:${data.angularZ.toFixed(2)}`);
                break;

            case 'speed_update':
                this.updateSpeed(data.speedMultiplier);
                this.log(`Speed updated: ${data.speedMultiplier.toFixed(1)}x`);
                break;

            case 'stopped':
                this.log('Robot stopped');
                this.updateRobotState({ action: 'stop', linearX: 0, linearY: 0, angularZ: 0 });
                break;

            case 'error':
                this.log('ERROR: ' + data.message);
                break;

            case 'pong':
                // Keepalive response
                break;
        }
    }

    sendCommand(action) {
        if (!this.isConnected) {
            this.log('Not connected to robot');
            return;
        }

        const message = {
            type: 'command',
            action: action
        };

        this.ws.send(JSON.stringify(message));
        this.currentAction = action;
    }

    sendSpeedChange(action, value = null) {
        if (!this.isConnected) return;

        const message = {
            type: 'speed',
            action: action
        };

        if (value !== null) {
            message.value = value;
        }

        this.ws.send(JSON.stringify(message));
    }

    sendStop() {
        if (!this.isConnected) return;

        this.ws.send(JSON.stringify({ type: 'stop' }));
        this.currentAction = 'stop';
    }

    setupEventListeners() {
        // Button click controls with continuous sending
        document.querySelectorAll('.control-btn[data-action]').forEach(btn => {
            btn.addEventListener('mousedown', () => {
                this.startContinuousCommand(btn.dataset.action);
                btn.classList.add('active');
            });

            btn.addEventListener('mouseup', () => {
                this.stopContinuousCommand();
                this.sendStop();
                btn.classList.remove('active');
            });

            btn.addEventListener('mouseleave', () => {
                this.stopContinuousCommand();
                this.sendStop();
                btn.classList.remove('active');
            });
        });

        // Stop button
        document.getElementById('stop-btn').addEventListener('click', () => {
            this.stopContinuousCommand();
            this.sendStop();
        });

        // Speed controls
        document.getElementById('speed-increase').addEventListener('click', () => {
            this.sendSpeedChange('increase');
        });

        document.getElementById('speed-decrease').addEventListener('click', () => {
            this.sendSpeedChange('decrease');
        });

        document.getElementById('speed-slider').addEventListener('input', (e) => {
            const value = parseFloat(e.target.value);
            this.sendSpeedChange('set', value);
        });
    }

    setupKeyboardControls() {
        const keyMap = {
            'w': 'w', 'W': 'w',
            'a': 'a', 'A': 'a',
            's': 's', 'S': 's',
            'd': 'd', 'D': 'd',
            'q': 'q', 'Q': 'q',
            'e': 'e', 'E': 'e',
            'z': 'z', 'Z': 'z',
            'c': 'c', 'C': 'c',
            'ArrowLeft': 'left',
            'ArrowRight': 'right',
            'ArrowUp': 'speed_up',
            'ArrowDown': 'speed_down',
            ' ': 'stop'
        };

        // Keep track of active command
        this.activeCommand = null;
        this.commandInterval = null;

        document.addEventListener('keydown', (e) => {
            // Prevent default behavior for arrow keys and space
            if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', ' '].includes(e.key)) {
                e.preventDefault();
            }

            const action = keyMap[e.key];
            if (!action) return;

            // Prevent key repeat
            if (this.pressedKeys.has(e.key)) return;
            this.pressedKeys.add(e.key);

            // Handle speed controls
            if (action === 'speed_up') {
                this.sendSpeedChange('increase');
                return;
            }
            if (action === 'speed_down') {
                this.sendSpeedChange('decrease');
                return;
            }

            // Handle stop
            if (action === 'stop') {
                this.stopContinuousCommand();
                this.sendStop();
                return;
            }

            // Handle movement - send command continuously while key is held
            this.startContinuousCommand(action);

            // Highlight corresponding button
            const btn = document.querySelector(`[data-action="${action}"]`);
            if (btn) btn.classList.add('active');
        });

        document.addEventListener('keyup', (e) => {
            this.pressedKeys.delete(e.key);

            const action = keyMap[e.key];
            if (!action || action === 'speed_up' || action === 'speed_down') return;

            // Stop continuous command
            this.stopContinuousCommand();

            // Send stop when key is released
            this.sendStop();

            // Remove highlight
            const btn = document.querySelector(`[data-action="${action}"]`);
            if (btn) btn.classList.remove('active');
        });
    }

    startContinuousCommand(action) {
        // Stop any existing command
        this.stopContinuousCommand();

        // Send command immediately
        this.sendCommand(action);

        // Continue sending command every 300ms while key is held
        this.commandInterval = setInterval(() => {
            this.sendCommand(action);
        }, 300);
    }

    stopContinuousCommand() {
        if (this.commandInterval) {
            clearInterval(this.commandInterval);
            this.commandInterval = null;
        }
    }

    updateConnectionStatus(connected) {
        const statusEl = document.getElementById('connection-status');
        statusEl.textContent = connected ? 'Connected' : 'Disconnected';
        statusEl.className = 'status ' + (connected ? 'connected' : 'disconnected');
    }

    updateSpeed(value) {
        this.speedMultiplier = value;
        document.getElementById('speed-display').textContent = `Speed: ${value.toFixed(1)}x`;
        document.getElementById('speed-value').textContent = `${value.toFixed(1)}x`;
        document.getElementById('speed-slider').value = value;
    }

    updateRobotState(data) {
        document.getElementById('current-action').textContent = data.action || 'None';
        document.getElementById('linear-x').textContent = (data.linearX || 0).toFixed(2);
        document.getElementById('linear-y').textContent = (data.linearY || 0).toFixed(2);
        document.getElementById('angular-z').textContent = (data.angularZ || 0).toFixed(2);
    }

    log(message) {
        const logEl = document.getElementById('log-messages');
        const timestamp = new Date().toLocaleTimeString();
        const logMessage = document.createElement('div');
        logMessage.textContent = `[${timestamp}] ${message}`;
        logEl.appendChild(logMessage);

        // Auto-scroll to bottom
        logEl.scrollTop = logEl.scrollHeight;

        // Keep only last 50 messages
        while (logEl.children.length > 50) {
            logEl.removeChild(logEl.firstChild);
        }
    }

    setupCameraControls() {
        const cameraStream = document.getElementById('camera-stream');
        const startBtn = document.getElementById('start-camera');
        const stopBtn = document.getElementById('stop-camera');
        const qualitySelect = document.getElementById('camera-quality');
        const statusText = document.getElementById('camera-status-text');

        startBtn.addEventListener('click', () => {
            this.startCamera();
        });

        stopBtn.addEventListener('click', () => {
            this.stopCamera();
        });

        qualitySelect.addEventListener('change', (e) => {
            if (this.cameraActive) {
                this.startCamera(); // Restart with new quality
            }
        });

        // Check camera stream status
        cameraStream.addEventListener('load', () => {
            statusText.textContent = 'Camera: Connected';
            statusText.style.background = 'rgba(16, 185, 129, 0.8)';
        });

        cameraStream.addEventListener('error', () => {
            statusText.textContent = 'Camera: Error';
            statusText.style.background = 'rgba(239, 68, 68, 0.8)';
        });
    }

    startCamera() {
        const cameraStream = document.getElementById('camera-stream');
        const qualitySelect = document.getElementById('camera-quality');
        const [width, height] = qualitySelect.value.split('x');

        // Use MJPEG stream from web_video_server on Raspberry Pi
        // web_video_server runs on port 8081 (to avoid conflict with Spring Boot on 8080)
        const raspberryPiIp = '192.168.0.3';
        const videoServerPort = '8081';

        // Format: http://ROBOT_IP:8081/stream?topic=/camera/image_raw
        const streamUrl = `http://${raspberryPiIp}:${videoServerPort}/stream?topic=/camera/image_raw&width=${width}&height=${height}`;

        cameraStream.src = streamUrl;
        this.cameraActive = true;
        this.log(`Camera started: ${streamUrl}`);

        document.getElementById('camera-status-text').textContent = 'Camera: Connecting...';
        document.getElementById('camera-status-text').style.background = 'rgba(251, 191, 36, 0.8)';
    }

    stopCamera() {
        const cameraStream = document.getElementById('camera-stream');
        cameraStream.src = '';
        this.cameraActive = false;
        this.log('Camera stopped');

        document.getElementById('camera-status-text').textContent = 'Camera: Disconnected';
        document.getElementById('camera-status-text').style.background = 'rgba(107, 114, 128, 0.8)';
    }
}

// Initialize controller when page loads
document.addEventListener('DOMContentLoaded', () => {
    window.robotController = new RobotController();
});