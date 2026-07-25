/**
 * read-receipts.js
 * Manages message read-status tracking and the ✓ / ✓✓ UI indicators.
 *
 * Status flow:
 *   sending  →  sent (✓ gray)  →  delivered (✓✓ gray)  →  read (✓✓ blue)
 */
const ReadReceiptManager = (() => {

    // Map of messageId → current status string
    const statusMap = {};

    /**
     * Build the HTML snippet for a receipt icon.
     * @param {string} status  One of: sending | sent | delivered | read | error
     * @returns {string}       HTML string.
     */
    function renderReceipt(status) {
        switch (status) {
            case 'sending':
                return '<span class="receipt receipt--sending" title="Sending">⏳</span>';
            case 'sent':
                return '<span class="receipt receipt--sent" title="Sent">✓</span>';
            case 'delivered':
                return '<span class="receipt receipt--delivered" title="Delivered">✓✓</span>';
            case 'read':
                return '<span class="receipt receipt--read" title="Read">✓✓</span>';
            case 'error':
                return '<span class="receipt receipt--error" title="Failed">✗</span>';
            default:
                return '';
        }
    }

    /**
     * Update the stored status for a message and refresh its DOM element.
     * @param {string} messageId  The UUID-based message ID.
     * @param {string} status     The new status string.
     */
    function updateReceipt(messageId, status) {
        statusMap[messageId] = status;
        const el = document.querySelector(`[data-message-id="${messageId}"] .receipt-container`);
        if (el) {
            el.innerHTML = renderReceipt(status);
        }
    }

    /**
     * Return the current status for a given message ID.
     * @param {string} messageId
     * @returns {string|undefined}
     */
    function getStatus(messageId) {
        return statusMap[messageId];
    }

    /**
     * Send a READ_RECEIPT message back to the server for a given message.
     * @param {string}   messageId  The message to acknowledge.
     * @param {string}   sender     The original sender's username.
     * @param {Function} sendFn     WebSocketClient.send function.
     */
    function sendReadReceipt(messageId, sender, sendFn) {
        if (typeof sendFn === 'function') {
            sendFn({ type: 'READ_RECEIPT', messageId, sender });
        }
    }

    /**
     * Called when a message bubble becomes visible in the active chat.
     * Marks messages from others as read and triggers receipt sending.
     */
    function onMessagesRead(messages, currentUser, sendFn) {
        messages.forEach(({ messageId, sender }) => {
            if (sender !== currentUser && statusMap[messageId] !== 'read') {
                sendReadReceipt(messageId, sender, sendFn);
            }
        });
    }

    return { renderReceipt, updateReceipt, getStatus, sendReadReceipt, onMessagesRead };
})();
