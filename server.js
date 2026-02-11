const express = require('express');
const bodyParser = require('body-parser');
const fs = require('fs');
const path = require('path');
const { exec } = require('child_process');
const axios = require('axios'); // Added for forwarding
const app = express();
const port = 3000;

// Middleware for parsing JSON bodies
app.use(bodyParser.json());

// Logging Middleware
app.use((req, res, next) => {
    const start = Date.now();
    const timestamp = new Date().toISOString();
    const { method, url, ip } = req;

    // Hook into response finish to get status and duration
    res.on('finish', () => {
        const duration = Date.now() - start;
        const status = res.statusCode;
        console.log(`[${timestamp}] ${ip} ${method} ${url} ${status} ${duration}ms`);
    });

    next();
});

// Health Check Endpoint
app.get('/health', (req, res) => {
    res.json({
        status: 'ok',
        uptime: process.uptime(),
        timestamp: new Date().toISOString(),
        version: '0.2.1'
    });
});

// Configure where to save uploaded files
const UPLOAD_DIR = path.join(__dirname, 'uploads');
if (!fs.existsSync(UPLOAD_DIR)) {
    fs.mkdirSync(UPLOAD_DIR);
}

// Helper to append to log file
const LOG_FILE = path.join(__dirname, 'app.log');
const logToFile = (message) => {
    const timestamp = new Date().toISOString();
    const logMessage = `[${timestamp}] ${message}\n`;
    fs.appendFile(LOG_FILE, logMessage, (err) => {
        if (err) console.error('Failed to write to log file:', err);
    });
};

// --- CONFIGURATION ---
// Target Feishu Webhook URL (from your python script logic)
// Replace this with the actual webhook you want to forward to.
// If you want to support multiple or dynamic webhooks, we can expand this.
const FEISHU_WEBHOOK_URL = "https://open.feishu.cn/open-apis/bot/v2/hook/52178044-6b94-4638-9533-315185973792"; 

// 1. Handle "app_usage" (Application Usage Stats)
app.post('/app_usage', (req, res) => {
    const data = req.body;
    console.log('Received app_usage:', JSON.stringify(data, null, 2));
    logToFile(`APP_USAGE: ${JSON.stringify(data)}`);

    // TODO: Process data (e.g., save to DB, generate report)
    // For now, just acknowledge.
    res.status(200).send({ status: 'received', type: 'app_usage' });
});

// 2. Handle "notification" (Notification Sync)
app.post('/notification', async (req, res) => {
    const data = req.body;
    console.log('Received notification:', JSON.stringify(data, null, 2));
    logToFile(`NOTIFICATION: ${JSON.stringify(data)}`);

    // Forward to Feishu
    if (FEISHU_WEBHOOK_URL) {
        try {
            const feishuPayload = {
                msg_type: "text",
                content: {
                    text: `📱 Notification from ${data.appName || 'Unknown App'}:\n${data.title || ''}\n${data.content || ''}`
                }
            };
            await axios.post(FEISHU_WEBHOOK_URL, feishuPayload);
            console.log('Forwarded notification to Feishu');
        } catch (error) {
            console.error('Error forwarding to Feishu:', error.message);
        }
    }

    res.status(200).send({ status: 'received', type: 'notification' });
});

// 3. Handle "clipboard" (Clipboard Sync)
app.post('/clipboard', async (req, res) => {
    const data = req.body;
    console.log('Received clipboard:', JSON.stringify(data, null, 2));
    logToFile(`CLIPBOARD: ${JSON.stringify(data)}`);

    // Forward to Feishu
    if (FEISHU_WEBHOOK_URL && data.content) {
        try {
             const feishuPayload = {
                msg_type: "text",
                content: {
                    text: `📋 Clipboard Content:\n${data.content}`
                }
            };
            await axios.post(FEISHU_WEBHOOK_URL, feishuPayload);
            console.log('Forwarded clipboard to Feishu');
        } catch (error) {
             console.error('Error forwarding clipboard to Feishu:', error.message);
        }
    }

    res.status(200).send({ status: 'received', type: 'clipboard' });
});

// Default route
app.get('/', (req, res) => {
    res.send('Phone Monitor Server is running.');
});

app.listen(port, () => {
    console.log(`Server running on port ${port}`);
    logToFile(`Server started on port ${port}`);
});
