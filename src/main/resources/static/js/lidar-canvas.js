/**
 * Lidar Canvas - Render laser scan data in real-time
 */
class LidarCanvas {
    constructor(canvasId, options = {}) {
        this.canvas = document.getElementById(canvasId);
        if (!this.canvas) {
            throw new Error(`Canvas with id "${canvasId}" not found`);
        }

        this.ctx = this.canvas.getContext('2d');
        
        // Configuration
        this.config = {
            pointSize: 2,
            pointColor: '#00ff00',
            robotSize: 10,
            robotColor: '#ff0000',
            gridColor: '#333333',
            backgroundColor: '#000000',
            maxRange: 12.0, // meters
            scale: 30, // pixels per meter
            showGrid: true,
            showRobot: true,
            showAngles: true,
            ...options
        };

        // Canvas setup
        this.setupCanvas();
        
        // Data
        this.scanData = null;
        this.lastUpdate = 0;
        
        // Animation
        this.animationId = null;
        this.isRunning = false;

        // Stats
        this.fps = 0;
        this.lastFrameTime = 0;
        this.frameCount = 0;
    }

    setupCanvas() {
        // Set canvas size
        const container = this.canvas.parentElement;
        this.canvas.width = container.clientWidth || 800;
        this.canvas.height = container.clientHeight || 600;

        // Center point
        this.centerX = this.canvas.width / 2;
        this.centerY = this.canvas.height / 2;

        console.log(`Canvas initialized: ${this.canvas.width}x${this.canvas.height}`);
    }

    /**
     * Update laser scan data
     */
    updateScan(scanData) {
        this.scanData = scanData;
        this.lastUpdate = Date.now();
    }

    /**
     * Start rendering loop
     */
    start() {
        if (this.isRunning) return;
        
        this.isRunning = true;
        this.animate();
        console.log('Lidar visualization started');
    }

    /**
     * Stop rendering loop
     */
    stop() {
        this.isRunning = false;
        if (this.animationId) {
            cancelAnimationFrame(this.animationId);
            this.animationId = null;
        }
        console.log('Lidar visualization stopped');
    }

    /**
     * Animation loop
     */
    animate() {
        if (!this.isRunning) return;

        this.render();
        this.animationId = requestAnimationFrame(() => this.animate());
    }

    /**
     * Main render function
     */
    render() {
        // Calculate FPS
        this.calculateFPS();

        // Clear canvas
        this.ctx.fillStyle = this.config.backgroundColor;
        this.ctx.fillRect(0, 0, this.canvas.width, this.canvas.height);

        // Draw grid
        if (this.config.showGrid) {
            this.drawGrid();
        }

        // Draw laser scan
        if (this.scanData) {
            this.drawLaserScan();
        }

        // Draw robot
        if (this.config.showRobot) {
            this.drawRobot();
        }

        // Draw angles
        if (this.config.showAngles) {
            this.drawAngles();
        }

        // Draw stats
        this.drawStats();
    }

    /**
     * Draw grid
     */
    drawGrid() {
        const { scale, gridColor, maxRange } = this.config;
        
        this.ctx.strokeStyle = gridColor;
        this.ctx.lineWidth = 1;

        // Draw circles (range rings)
        for (let r = 1; r <= maxRange; r++) {
            const radius = r * scale;
            this.ctx.beginPath();
            this.ctx.arc(this.centerX, this.centerY, radius, 0, 2 * Math.PI);
            this.ctx.stroke();

            // Label
            this.ctx.fillStyle = gridColor;
            this.ctx.font = '10px monospace';
            this.ctx.fillText(`${r}m`, this.centerX + radius - 20, this.centerY - 5);
        }

        // Draw crosshair
        this.ctx.beginPath();
        this.ctx.moveTo(this.centerX - 20, this.centerY);
        this.ctx.lineTo(this.centerX + 20, this.centerY);
        this.ctx.moveTo(this.centerX, this.centerY - 20);
        this.ctx.lineTo(this.centerX, this.centerY + 20);
        this.ctx.stroke();
    }

    /**
     * Draw laser scan points
     */
    drawLaserScan() {
        const { ranges, angle_min, angle_increment } = this.scanData;
        const { scale, pointSize, pointColor } = this.config;

        if (!ranges || ranges.length === 0) return;

        this.ctx.fillStyle = pointColor;

        let validPoints = 0;

        for (let i = 0; i < ranges.length; i++) {
            const range = ranges[i];
            
            // Skip invalid readings
            if (!isFinite(range) || range <= 0 || range > this.config.maxRange) {
                continue;
            }

            validPoints++;

            // Calculate angle (ROS coordinates: counter-clockwise from front)
            const angle = angle_min + (i * angle_increment);
            
            // Convert to canvas coordinates
            // ROS: 0° = front (x+), 90° = left (y+)
            // Canvas: 0° = right, 90° = down
            // So we need to rotate by 90° and flip Y
            const x = this.centerX + (range * scale * Math.cos(angle - Math.PI / 2));
            const y = this.centerY + (range * scale * Math.sin(angle - Math.PI / 2));

            // Draw point
            this.ctx.beginPath();
            this.ctx.arc(x, y, pointSize, 0, 2 * Math.PI);
            this.ctx.fill();
        }

        // Store valid point count for stats
        this.validPoints = validPoints;
    }

    /**
     * Draw robot at center
     */
    drawRobot() {
        const { robotSize, robotColor } = this.config;

        // Draw robot body (circle)
        this.ctx.fillStyle = robotColor;
        this.ctx.beginPath();
        this.ctx.arc(this.centerX, this.centerY, robotSize, 0, 2 * Math.PI);
        this.ctx.fill();

        // Draw direction indicator (pointing up/forward)
        this.ctx.strokeStyle = robotColor;
        this.ctx.lineWidth = 2;
        this.ctx.beginPath();
        this.ctx.moveTo(this.centerX, this.centerY);
        this.ctx.lineTo(this.centerX, this.centerY - robotSize * 1.5);
        this.ctx.stroke();
    }

    /**
     * Draw angle indicators
     */
    drawAngles() {
        const radius = this.config.maxRange * this.config.scale;
        
        this.ctx.fillStyle = '#666666';
        this.ctx.font = '12px monospace';

        // 0° (Front)
        this.ctx.fillText('0°', this.centerX - 10, this.centerY - radius - 10);
        
        // 90° (Left)
        this.ctx.fillText('90°', this.centerX - radius - 30, this.centerY + 5);
        
        // 180° (Back)
        this.ctx.fillText('180°', this.centerX - 15, this.centerY + radius + 20);
        
        // 270° (Right)
        this.ctx.fillText('270°', this.centerX + radius + 10, this.centerY + 5);
    }

    /**
     * Draw statistics overlay
     */
    drawStats() {
        const timeSinceUpdate = Date.now() - this.lastUpdate;
        const isStale = timeSinceUpdate > 1000;

        this.ctx.fillStyle = isStale ? '#ff0000' : '#00ff00';
        this.ctx.font = '12px monospace';

        const stats = [
            `FPS: ${this.fps}`,
            `Points: ${this.validPoints || 0}`,
            `Last update: ${timeSinceUpdate}ms ago`,
            `Status: ${isStale ? 'STALE' : 'OK'}`
        ];

        stats.forEach((stat, i) => {
            this.ctx.fillText(stat, 10, 20 + (i * 15));
        });
    }

    /**
     * Calculate FPS
     */
    calculateFPS() {
        const now = performance.now();
        this.frameCount++;

        if (now - this.lastFrameTime >= 1000) {
            this.fps = Math.round(this.frameCount * 1000 / (now - this.lastFrameTime));
            this.frameCount = 0;
            this.lastFrameTime = now;
        }
    }

    /**
     * Update configuration
     */
    updateConfig(newConfig) {
        this.config = { ...this.config, ...newConfig };
    }

    /**
     * Clear canvas
     */
    clear() {
        this.ctx.fillStyle = this.config.backgroundColor;
        this.ctx.fillRect(0, 0, this.canvas.width, this.canvas.height);
    }

    /**
     * Resize canvas
     */
    resize() {
        this.setupCanvas();
    }
}

// Export for use in other scripts
if (typeof module !== 'undefined' && module.exports) {
    module.exports = LidarCanvas;
}
