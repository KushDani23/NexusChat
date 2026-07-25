/**
 * app.js
 * NexusChat – the main UI controller.
 *
 * Orchestrates login, sidebar, chat switching, message rendering,
 * typing indicators, read receipts, and theme toggling.
 */
const ChatApp = (() => {

    // ── State ─────────────────────────────────────────────────────────────────
    let currentUser      = null;   // Logged-in username
    let currentChatId    = 'group'; // Currently active chat ('group' or username)
    let onlineUsers      = [];
    let typingTimers     = {};     // username → timeout handle
    let registrationDone = false;

    // Per-chat message history:  chatId → [ {messageId, sender, content, timestamp, isMine, type} ]
    const chatHistory = { group: [] };

    // Per-chat unread counts:  chatId → number
    const unreadCounts = {};

    // Per-chat last-message preview: chatId → { text, time }
    const lastMessages = {};

    // Debounce timer for typing stop
    let typingStopTimer = null;

    // ── DOM helpers ───────────────────────────────────────────────────────────
    const $ = id => document.getElementById(id);
    const el = (tag, cls, html) => {
        const e = document.createElement(tag);
        if (cls)  e.className   = cls;
        if (html) e.innerHTML   = html;
        return e;
    };

    // ── Initialisation ────────────────────────────────────────────────────────

    function init() {
        ThemeManager.init();

        // Theme toggle
        const themeBtn = $('themeToggle');
        if (themeBtn) themeBtn.addEventListener('click', ThemeManager.toggleTheme);

        // Login
        $('connectBtn').addEventListener('click', handleConnect);
        $('usernameInput').addEventListener('keydown', e => {
            if (e.key === 'Enter') handleConnect();
        });

        // Message sending
        $('sendBtn').addEventListener('click', sendMessage);
        $('messageInput').addEventListener('keydown', e => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                sendMessage();
            }
        });

        // Auto-grow textarea
        $('messageInput').addEventListener('input', autoGrowTextarea);

        // Typing indicator
        $('messageInput').addEventListener('input', handleTypingInput);

        // Sidebar search
        $('searchInput').addEventListener('input', filterChats);

        // Logout
        $('logoutBtn').addEventListener('click', handleLogout);

        // Clear chat
        $('clearChatBtn').addEventListener('click', clearChat);

        // Mobile sidebar toggle
        const sidebarToggle = $('sidebarToggle');
        if (sidebarToggle) sidebarToggle.addEventListener('click', toggleMobileSidebar);

        // Sidebar overlay click – close sidebar
        const overlay = $('sidebarOverlay');
        if (overlay) overlay.addEventListener('click', closeMobileSidebar);

        // Click outside sidebar (mobile)
        document.addEventListener('click', handleOutsideClick);
    }

    // ── Login flow ────────────────────────────────────────────────────────────

    function handleConnect() {
        const input = $('usernameInput').value.trim();
        if (!input) return showLoginError('Please enter a username.');
        if (input.length < 2) return showLoginError('Username must be at least 2 characters.');
        if (input.length > 20) return showLoginError('Username must be at most 20 characters.');
        if (!/^[a-zA-Z0-9_\-]+$/.test(input)) {
            return showLoginError('Only letters, numbers, _ and - are allowed.');
        }

        currentUser = input;
        showLoginLoading(true);

        WebSocketClient.connect(currentUser, {
            onOpen:        () => {},          // registration sent automatically inside websocket.js
            onClose:       handleDisconnect,
            onMessage:     handleServerMessage,
            onReconnecting: (attempt, max) => {
                showToast(`Reconnecting… (${attempt}/${max})`);
                showBanner('reconnecting', `Reconnecting… (attempt ${attempt} of ${max})`);
            }
        });
    }

    function showLoginError(msg) {
        const errEl = $('loginError');
        errEl.textContent = msg;
        errEl.classList.add('visible');
        setTimeout(() => errEl.classList.remove('visible'), 3000);
    }

    function showLoginLoading(on) {
        const btn = $('connectBtn');
        btn.disabled = on;
        btn.textContent = on ? 'Connecting…' : 'Get Started →';
    }

    // ── WebSocket message dispatcher ──────────────────────────────────────────

    function handleServerMessage(data) {
        switch (data.type) {
            case 'REGISTER_RESPONSE': onRegisterResponse(data); break;
            case 'GROUP':             onGroupMessage(data);     break;
            case 'PRIVATE':           onPrivateMessage(data);   break;
            case 'TYPING':            onTyping(data);           break;
            case 'USERS_LIST':        onUsersList(data);        break;
            case 'USER_JOINED':       onUserJoined(data);       break;
            case 'USER_LEFT':         onUserLeft(data);         break;
            case 'MESSAGE_STATUS':    onMessageStatus(data);    break;
            case 'READ_RECEIPT':      onReadReceipt(data);      break;
            case 'ERROR':             showToast('Error: ' + data.message); break;
        }
    }

    // ── Register response ─────────────────────────────────────────────────────

    function onRegisterResponse(data) {
        showLoginLoading(false);
        if (data.success) {
            registrationDone = true;
            showChatUI();
            showBanner('connected', '✓ Connected successfully');
        } else {
            currentUser = null;
            showLoginError(data.error || 'Registration failed.');
        }
    }

    function showChatUI() {
        $('loginScreen').classList.add('hidden');
        $('chatUI').classList.remove('hidden');
        $('currentUserName').textContent = currentUser;
        $('currentUserAvatar').textContent = getAvatar(currentUser);
        $('currentUserAvatar').style.background = getAvatarColor(currentUser);
        switchChat('group');
        showEmptyChat();
        requestAnimationFrame(() => $('messageInput').focus());
    }

    // ── Group messages ────────────────────────────────────────────────────────

    function onGroupMessage(data) {
        const isMine = data.sender === currentUser;
        const entry  = {
            messageId: data.messageId,
            sender:    data.sender,
            content:   data.content,
            timestamp: data.timestamp,
            isMine,
            type: 'group'
        };
        chatHistory.group.push(entry);
        updateLastMessage('group', data.content, data.timestamp);

        if (currentChatId === 'group') {
            renderMessage('group', entry);
            scrollToBottom();
            // Mark as read immediately if in active chat and not mine
            if (!isMine) {
                ReadReceiptManager.sendReadReceipt(data.messageId, data.sender,
                    payload => WebSocketClient.send(payload));
            }
        } else {
            incrementUnread('group');
        }
    }

    // ── Private messages ──────────────────────────────────────────────────────

    function onPrivateMessage(data) {
        const isMine   = data.sender === currentUser;
        const chatId   = isMine ? data.target : data.sender;
        const entry    = {
            messageId: data.messageId,
            sender:    data.sender,
            content:   data.content,
            timestamp: data.timestamp,
            isMine,
            type: 'private'
        };

        if (!chatHistory[chatId]) chatHistory[chatId] = [];
        chatHistory[chatId].push(entry);
        updateLastMessage(chatId, data.content, data.timestamp);

        if (currentChatId === chatId) {
            renderMessage(chatId, entry);
            scrollToBottom();
            if (!isMine) {
                ReadReceiptManager.sendReadReceipt(data.messageId, data.sender,
                    payload => WebSocketClient.send(payload));
            }
        } else {
            incrementUnread(chatId);
            // Show notification badge
            updateSidebarItem(chatId);
        }
    }

    // ── Typing indicator ──────────────────────────────────────────────────────

    function onTyping(data) {
        const { sender, isTyping, target } = data;
        if (sender === currentUser) return;

        // Only show typing in the relevant chat
        const relevantChat = target
            ? (currentChatId === data.sender || currentChatId === target)
            : currentChatId === 'group';

        if (!relevantChat) return;

        if (isTyping) {
            showTypingIndicator(sender);
            clearTimeout(typingTimers[sender]);
            typingTimers[sender] = setTimeout(() => hideTypingIndicator(sender), 4000);
        } else {
            clearTimeout(typingTimers[sender]);
            hideTypingIndicator(sender);
        }
    }

    function showTypingIndicator(sender) {
        let indicator = $('typingIndicator');
        if (!indicator) return;
        indicator.querySelector('.typing-name').textContent = sender + ' is typing';
        indicator.classList.remove('hidden');
    }

    function hideTypingIndicator(sender) {
        const indicator = $('typingIndicator');
        if (indicator) indicator.classList.add('hidden');
    }

    // ── Users list ────────────────────────────────────────────────────────────

    function onUsersList(data) {
        onlineUsers = data.users || [];
        renderSidebar();
    }

    function onUserJoined(data) {
        const { username } = data;
        if (!onlineUsers.includes(username)) onlineUsers.push(username);
        addSystemMessage('group', `${username} joined the chat`);
        renderSidebar();
    }

    function onUserLeft(data) {
        const { username } = data;
        onlineUsers = onlineUsers.filter(u => u !== username);
        addSystemMessage('group', `${username} left the chat`);
        renderSidebar();
    }

    // ── Message status (read receipts) ────────────────────────────────────────

    function onMessageStatus(data) {
        ReadReceiptManager.updateReceipt(data.messageId, data.status);
    }

    function onReadReceipt(data) {
        ReadReceiptManager.updateReceipt(data.messageId, 'read');
    }

    // ── Sending messages ──────────────────────────────────────────────────────

    function sendMessage() {
        const input   = $('messageInput');
        const content = input.value.trim();
        if (!content || !WebSocketClient.isConnected()) return;

        input.value = '';
        input.style.height = 'auto';
        stopTypingIndicator();

        // Send button animation
        const sendBtn = $('sendBtn');
        if (sendBtn) {
            sendBtn.classList.add('sending');
            setTimeout(() => sendBtn.classList.remove('sending'), 400);
        }

        if (currentChatId === 'group') {
            WebSocketClient.send({ type: 'GROUP', content });
        } else {
            WebSocketClient.send({ type: 'PRIVATE', target: currentChatId, content });
        }
    }

    // ── Typing events ─────────────────────────────────────────────────────────

    function handleTypingInput() {
        if (!WebSocketClient.isConnected()) return;
        const target = currentChatId === 'group' ? null : currentChatId;

        WebSocketClient.send({ type: 'TYPING', isTyping: true, ...(target && { target }) });

        clearTimeout(typingStopTimer);
        typingStopTimer = setTimeout(stopTypingIndicator, 2000);
    }

    function stopTypingIndicator() {
        if (!WebSocketClient.isConnected()) return;
        const target = currentChatId === 'group' ? null : currentChatId;
        WebSocketClient.send({ type: 'TYPING', isTyping: false, ...(target && { target }) });
    }

    // ── Chat switching ────────────────────────────────────────────────────────

    function switchChat(chatId) {
        currentChatId = chatId;

        // Close mobile sidebar when a chat is selected
        closeMobileSidebar();

        // Update sidebar active state
        document.querySelectorAll('.chat-item').forEach(item => {
            item.classList.toggle('active', item.dataset.chatId === chatId);
        });

        // Update header
        const isGroup = chatId === 'group';
        const isOnline = !isGroup && onlineUsers.includes(chatId);

        $('chatName').textContent = isGroup ? 'Group Chat' : chatId;
        $('chatAvatar').textContent = isGroup ? '👥' : getAvatar(chatId);
        $('chatAvatar').style.background = isGroup ? 'var(--accent-group)' : getAvatarColor(chatId);

        // Update status text and dot
        const statusEl = $('chatStatus');
        const dotEl    = $('statusDotHeader');
        const ringEl   = $('headerAvatarRing');

        if (isGroup) {
            const count = onlineUsers.length;
            statusEl.innerHTML = `<span class="status-dot-header" id="statusDotHeader"></span>${count} member${count !== 1 ? 's' : ''} online`;
            if (ringEl) ringEl.classList.remove('online');
        } else {
            const onlineText = isOnline ? 'Online' : 'Offline';
            statusEl.innerHTML = `<span class="status-dot-header${isOnline ? ' visible' : ''}" id="statusDotHeader"></span>${onlineText}`;
            statusEl.className = isOnline ? 'online-status' : '';
            if (ringEl) ringEl.classList.toggle('online', isOnline);
        }

        // Clear unread
        clearUnread(chatId);

        // Render messages
        const messagesEl = $('messages');
        messagesEl.innerHTML = '';

        if (!chatHistory[chatId]) chatHistory[chatId] = [];

        if (chatHistory[chatId].length === 0) {
            showEmptyChat();
        } else {
            chatHistory[chatId].forEach(entry => renderMessage(chatId, entry, false));
        }

        scrollToBottom();

        // Mark all unread messages as read
        if (chatHistory[chatId]) {
            const unread = chatHistory[chatId].filter(e => !e.isMine);
            ReadReceiptManager.onMessagesRead(unread, currentUser,
                payload => WebSocketClient.send(payload));
        }

        // Hide typing indicator
        $('typingIndicator').classList.add('hidden');

        // Focus input
        $('messageInput').focus();
    }

    // ── Sidebar rendering ─────────────────────────────────────────────────────

    function renderSidebar() {
        const list    = $('chatList');
        const search  = $('searchInput').value.toLowerCase();
        list.innerHTML = '';

        // Group chat item (always first)
        if (!search || 'group chat'.includes(search)) {
            list.appendChild(buildChatItem('group', '👥', 'Group Chat', 'var(--accent-group)'));
        }

        // One item per online user (excluding ourselves)
        onlineUsers
            .filter(u => u !== currentUser)
            .filter(u => !search || u.toLowerCase().includes(search))
            .forEach(u => {
                list.appendChild(buildChatItem(u, getAvatar(u), u, getAvatarColor(u)));
            });
    }

    function buildChatItem(chatId, avatarText, displayName, avatarBg) {
        const isActive = currentChatId === chatId;
        const last     = lastMessages[chatId];
        const unread   = unreadCounts[chatId] || 0;
        const isOnline = chatId === 'group' || onlineUsers.includes(chatId);

        const item = el('div', `chat-item${isActive ? ' active' : ''}`);
        item.dataset.chatId = chatId;

        item.innerHTML = `
            <div class="chat-avatar" style="background:${avatarBg}">
                ${avatarText}
                ${chatId !== 'group' ? `<span class="online-dot ${isOnline ? 'online' : 'offline'}"></span>` : ''}
            </div>
            <div class="chat-info">
                <div class="chat-header-row">
                    <span class="chat-name">${escapeHtml(displayName)}</span>
                    <span class="chat-time">${last ? last.time : ''}</span>
                </div>
                <div class="chat-preview-row">
                    <span class="chat-preview">${last ? escapeHtml(last.text) : 'No messages yet'}</span>
                    ${unread > 0 ? `<span class="unread-badge">${unread}</span>` : ''}
                </div>
            </div>`;

        item.addEventListener('click', () => switchChat(chatId));
        return item;
    }

    function updateSidebarItem(chatId) {
        const existing = document.querySelector(`[data-chat-id="${chatId}"]`);
        if (existing) {
            const isOnline  = chatId === 'group' || onlineUsers.includes(chatId);
            const last      = lastMessages[chatId];
            const unread    = unreadCounts[chatId] || 0;
            const previewEl = existing.querySelector('.chat-preview');
            const timeEl    = existing.querySelector('.chat-time');
            const badgeEl   = existing.querySelector('.unread-badge');

            if (previewEl && last) previewEl.textContent = last.text;
            if (timeEl    && last) timeEl.textContent    = last.time;
            if (badgeEl) {
                badgeEl.textContent = unread > 0 ? unread : '';
                badgeEl.style.display = unread > 0 ? '' : 'none';
            } else if (unread > 0) {
                const pr = existing.querySelector('.chat-preview-row');
                const nb = el('span', 'unread-badge', String(unread));
                if (pr) pr.appendChild(nb);
            }
        } else {
            renderSidebar();
        }
    }

    // ── Message rendering ─────────────────────────────────────────────────────

    function renderMessage(chatId, entry, doScroll = true) {
        const messagesEl = $('messages');

        // Remove empty-chat placeholder if present
        const emptyEl = messagesEl.querySelector('.empty-chat');
        if (emptyEl) emptyEl.remove();

        const { messageId, sender, content, timestamp, isMine, type } = entry;

        const wrapper = el('div', `message-wrapper ${isMine ? 'mine' : 'theirs'}`);
        wrapper.dataset.messageId = messageId;

        if (!isMine) {
            // Avatar
            const av = el('div', 'msg-avatar');
            av.textContent = getAvatar(sender);
            av.style.background = getAvatarColor(sender);
            av.title = sender;
            wrapper.appendChild(av);
        }

        const bubble = el('div', `bubble ${isMine ? 'bubble--sent' : 'bubble--received'}`);

        // Show sender name in group chat for received messages
        if (type === 'group' && !isMine) {
            const nameEl = el('div', 'bubble-sender', escapeHtml(sender));
            nameEl.style.color = getAvatarColor(sender);
            bubble.appendChild(nameEl);
        }

        const textEl = el('div', 'bubble-text');
        textEl.innerHTML = formatMessageContent(content);
        bubble.appendChild(textEl);

        const metaEl = el('div', 'bubble-meta');
        metaEl.innerHTML = `
            <span class="bubble-time">${escapeHtml(timestamp || '')}</span>
            ${isMine ? `<span class="receipt-container">${ReadReceiptManager.renderReceipt('sent')}</span>` : ''}`;
        bubble.appendChild(metaEl);

        wrapper.appendChild(bubble);
        messagesEl.appendChild(wrapper);

        if (doScroll) scrollToBottom();
    }

    function addSystemMessage(chatId, text) {
        if (!chatHistory[chatId]) chatHistory[chatId] = [];
        const entry = { messageId: 'sys_' + Date.now(), sender: 'system', content: text, timestamp: now(), isMine: false, type: 'system' };
        chatHistory[chatId].push(entry);

        if (currentChatId === chatId) {
            const messagesEl = $('messages');
            const div = el('div', 'system-message', escapeHtml(text));
            messagesEl.appendChild(div);
            scrollToBottom();
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    function scrollToBottom() {
        const m = $('messages');
        requestAnimationFrame(() => { m.scrollTop = m.scrollHeight; });
    }

    function updateLastMessage(chatId, text, time) {
        lastMessages[chatId] = { text, time };
        updateSidebarItem(chatId);
    }

    function incrementUnread(chatId) {
        unreadCounts[chatId] = (unreadCounts[chatId] || 0) + 1;
        updateSidebarItem(chatId);
    }

    function clearUnread(chatId) {
        unreadCounts[chatId] = 0;
        const badge = document.querySelector(`[data-chat-id="${chatId}"] .unread-badge`);
        if (badge) badge.style.display = 'none';
    }

    function filterChats() { renderSidebar(); }

    function clearChat() {
        chatHistory[currentChatId] = [];
        $('messages').innerHTML = '';
        showToast('Chat cleared');
    }

    function handleLogout() {
        WebSocketClient.disconnect();
        $('chatUI').classList.add('hidden');
        $('loginScreen').classList.remove('hidden');
        $('usernameInput').value = '';
        currentUser = null;
        registrationDone = false;
        Object.keys(chatHistory).forEach(k => delete chatHistory[k]);
        chatHistory.group = [];
        onlineUsers = [];
        showLoginLoading(false);
        hideBanner();
    }

    function handleDisconnect() {
        if (registrationDone) {
            showToast('Connection lost. Please refresh.');
            showBanner('disconnected', 'Disconnected — check your connection');
        }
    }

    function handleOutsideClick(e) {
        // Mobile: close sidebar if clicking outside
        const sidebar = $('sidebar');
        const toggleBtn = $('sidebarToggle');
        if (sidebar && toggleBtn &&
            !sidebar.contains(e.target) &&
            !toggleBtn.contains(e.target)) {
            closeMobileSidebar();
        }
    }

    // ── Mobile sidebar helpers ────────────────────────────────────────────────

    function toggleMobileSidebar() {
        const sidebar = $('sidebar');
        const overlay = $('sidebarOverlay');
        if (!sidebar) return;
        const isOpen = sidebar.classList.toggle('open');
        if (overlay) overlay.classList.toggle('visible', isOpen);
    }

    function closeMobileSidebar() {
        const sidebar = $('sidebar');
        const overlay = $('sidebarOverlay');
        if (sidebar) sidebar.classList.remove('open');
        if (overlay) overlay.classList.remove('visible');
    }

    // ── Connection Banner ─────────────────────────────────────────────────────

    function showBanner(type, text) {
        const banner = $('connectionBanner');
        const bannerText = $('bannerText');
        if (!banner) return;
        banner.className = `show ${type}`;
        if (bannerText) bannerText.textContent = text;
        // Auto-hide 'connected' banner after 3s
        if (type === 'connected') {
            setTimeout(hideBanner, 3000);
        }
    }

    function hideBanner() {
        const banner = $('connectionBanner');
        if (banner) banner.className = '';
    }

    // ── Auto-grow textarea ────────────────────────────────────────────────────

    function autoGrowTextarea() {
        const el = $('messageInput');
        if (!el) return;
        el.style.height = 'auto';
        el.style.height = Math.min(el.scrollHeight, 130) + 'px';
    }

    // ── Empty chat placeholder ────────────────────────────────────────────────

    function showEmptyChat() {
        const messagesEl = $('messages');
        if (!messagesEl) return;
        const isGroup = currentChatId === 'group';
        const empty = el('div', 'empty-chat');
        empty.innerHTML = `
            <div class="empty-chat-icon">${isGroup ? '👥' : '💬'}</div>
            <h3>${isGroup ? 'Group Chat' : currentChatId}</h3>
            <p>${isGroup ? 'Send a message to start the group conversation!' : `Start a private conversation with ${currentChatId}`}</p>
        `;
        messagesEl.appendChild(empty);
    }

    function showToast(msg) {
        let toast = $('toast');
        if (!toast) {
            toast = el('div', 'toast');
            toast.id = 'toast';
            document.body.appendChild(toast);
        }
        toast.textContent = msg;
        toast.classList.add('show');
        setTimeout(() => toast.classList.remove('show'), 3000);
    }

    function now() {
        return new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' });
    }

    function getAvatar(username) {
        return username ? username.charAt(0).toUpperCase() : '?';
    }

    const AVATAR_COLORS = [
        '#6B73FF','#9D50BB','#FF6B6B','#FFD93D',
        '#6BCB77','#4ECDC4','#FF9A3C','#C77DFF'
    ];

    function getAvatarColor(username) {
        if (!username) return AVATAR_COLORS[0];
        let hash = 0;
        for (let i = 0; i < username.length; i++) {
            hash = username.charCodeAt(i) + ((hash << 5) - hash);
        }
        return AVATAR_COLORS[Math.abs(hash) % AVATAR_COLORS.length];
    }

    function escapeHtml(str) {
        if (!str) return '';
        return str
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    function formatMessageContent(content) {
        // Auto-linkify URLs, then escape HTML
        const escaped = escapeHtml(content);
        // Convert newlines
        return escaped.replace(/\n/g, '<br>');
    }

    return { init };
})();

// Bootstrap
document.addEventListener('DOMContentLoaded', ChatApp.init);
