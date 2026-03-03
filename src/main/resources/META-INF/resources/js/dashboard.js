/**
 * CAUSA RCA Dashboard - JavaScript
 * Handles DataTables initialization, tab switching, sidebar collapse, and workload scanning
 */

// Guards to prevent concurrent/duplicate DataTable initialization
let dashboardTableLoading = false;
let workloadsTableLoading = false;

// Initialize when DOM is ready
$(document).ready(function() {
    console.log('CAUSA Dashboard loaded');
    
    // Initialize tab switching
    initTabSwitching();
    
    // Initialize sidebar toggle
    initSidebarToggle();
    
    // Load dashboard data on page load
    loadDashboard();
    
    // Check if the analyses table exists
    if ($('#analysesTable').length) {
        initAnalysesTable();
    }
});

/**
 * Activate a specific tab by name, updating URL hash and loading data.
 */
function activateTab(targetTab) {
    const tabButtons = document.querySelectorAll('.tab-button');
    const tabContents = document.querySelectorAll('.tab-content');

    tabButtons.forEach(btn => btn.classList.remove('active'));
    tabContents.forEach(content => content.classList.remove('active'));

    const button = document.querySelector(`.tab-button[data-tab="${targetTab}"]`);
    const content = document.getElementById(`${targetTab}-tab`);

    if (button) button.classList.add('active');
    if (content) content.classList.add('active');

    // Persist active tab in URL hash so Refresh stays on the same tab
    window.location.hash = targetTab;

    if (targetTab === 'dashboard') {
        loadDashboard();
    } else if (targetTab === 'workloads') {
        loadWorkloads();
    }

    console.log(`Switched to ${targetTab} tab`);
}

/**
 * Initialize tab switching functionality
 */
function initTabSwitching() {
    const tabButtons = document.querySelectorAll('.tab-button');

    tabButtons.forEach(button => {
        button.addEventListener('click', () => {
            const targetTab = button.getAttribute('data-tab');
            activateTab(targetTab);
        });
    });

    // Restore active tab from URL hash on page load (survives Refresh)
    const hash = window.location.hash.replace('#', '');
    const validTabs = ['dashboard', 'analysis', 'workloads', 'about'];
    if (hash && validTabs.includes(hash)) {
        activateTab(hash);
    }
}

/**
 * Initialize sidebar toggle functionality
 */
function initSidebarToggle() {
    const toggleButton = document.getElementById('sidebarToggle');
    const sidebar = document.getElementById('sidebar');
    const mainContent = document.getElementById('mainContent');
    
    if (toggleButton && sidebar && mainContent) {
        toggleButton.addEventListener('click', () => {
            sidebar.classList.toggle('collapsed');
            mainContent.classList.toggle('expanded');
            console.log('Sidebar toggled');
        });
    }
}

/**
 * Initialize the analyses DataTable
 */
function initAnalysesTable() {
    const table = $('#analysesTable').DataTable({
        // Pagination
        pageLength: 25,
        lengthMenu: [[10, 25, 50, 100, -1], [10, 25, 50, 100, "All"]],
        
        // Ordering
        order: [[0, 'desc']], // Sort by timestamp (first column) descending
        
        // Column definitions
        columnDefs: [
            {
                targets: 0, // Timestamp column
                type: 'date'
            },
            {
                targets: -1, // Actions column (last column)
                orderable: false,
                searchable: false
            },
            {
                targets: 4, // Progress column
                orderable: false
            }
        ],
        
        // Styling
        dom: '<"top"lf>rt<"bottom"ip><"clear">',
        
        // Language customization
        language: {
            search: "Search:",
            lengthMenu: "Show _MENU_ entries",
            info: "Showing _START_ to _END_ of _TOTAL_ analyses",
            infoEmpty: "Showing 0 to 0 of 0 analyses",
            infoFiltered: "(filtered from _MAX_ total analyses)",
            paginate: {
                first: "First",
                last: "Last",
                next: "Next",
                previous: "Previous"
            },
            emptyTable: "No analyses available"
        },
        
        // Responsive
        responsive: true,
        
        // Auto width
        autoWidth: false
    });
    
    console.log('Analyses DataTable initialized successfully');
}

/**
 * Load dashboard data (unhealthy analyses)
 */
function loadDashboard() {
    console.log('Loading dashboard data...');
    
    // Check if dashboard elements exist
    const loadingIndicator = document.getElementById('dashboardLoadingIndicator');
    const dashboardContent = document.getElementById('dashboardContent');
    const emptyState = document.getElementById('dashboardEmptyState');
    const tableContainer = document.getElementById('dashboardTableContainer');
    
    if (!loadingIndicator || !dashboardContent) {
        console.log('Dashboard elements not found, skipping load');
        return;
    }
    
    // Prevent concurrent or duplicate initialization
    if (dashboardTableLoading || $.fn.DataTable.isDataTable('#dashboardTable')) {
        console.log('Dashboard table already initialized or loading');
        return;
    }
    dashboardTableLoading = true;

    // Show loading
    loadingIndicator.style.display = 'block';
    dashboardContent.style.display = 'none';
    
    // Fetch unhealthy analyses from API
    fetch('/api/analyses/unhealthy?pageSize=100')
        .then(response => {
            if (!response.ok) {
                throw new Error('Failed to fetch dashboard data');
            }
            return response.json();
        })
        .then(data => {
            // Hide loading
            if (loadingIndicator) loadingIndicator.style.display = 'none';
            if (dashboardContent) dashboardContent.style.display = 'block';
            
            if (!data.analyses || data.analyses.length === 0) {
                // Show empty state
                if (emptyState) emptyState.style.display = 'block';
                if (tableContainer) tableContainer.style.display = 'none';
            } else {
                // Show table with data
                if (emptyState) emptyState.style.display = 'none';
                if (tableContainer) tableContainer.style.display = 'block';
                populateDashboardTable(data.analyses);
            }
        })
        .catch(error => {
            dashboardTableLoading = false;
            console.error('Error loading dashboard:', error);
            if (loadingIndicator) {
                const errDiv = document.createElement('div');
                errDiv.className = 'error-message';
                const iconSpan = document.createElement('span');
                iconSpan.textContent = '⚠';
                const msgSpan = document.createElement('span');
                msgSpan.textContent = 'Failed to load dashboard data';
                errDiv.appendChild(iconSpan);
                errDiv.appendChild(msgSpan);
                loadingIndicator.textContent = '';
                loadingIndicator.appendChild(errDiv);
            }
        });
}

/**
 * Populate dashboard table with unhealthy analyses
 */
function populateDashboardTable(analyses) {
    const tbody = document.getElementById('dashboardTableBody');
    
    if (!tbody) {
        console.error('Dashboard table body not found');
        return;
    }
    
    tbody.innerHTML = '';
    
    analyses.forEach(analysis => {
        const row = document.createElement('tr');
        row.className = 'analysis-row';
        
        const issueTitle = analysis.report?.title || analysis.report?.issue || 'Issue detected';

        // Timestamp cell
        const tdTimestamp = document.createElement('td');
        tdTimestamp.className = 'timestamp-cell';
        tdTimestamp.setAttribute('data-order', analysis.timestamp);
        const tsDiv = document.createElement('div');
        tsDiv.className = 'timestamp';
        tsDiv.textContent = formatTimestamp(analysis.timestamp);
        tdTimestamp.appendChild(tsDiv);

        // Namespace cell
        const tdNamespace = document.createElement('td');
        tdNamespace.className = 'namespace';
        tdNamespace.textContent = analysis.namespace;

        // Pod cell
        const tdPod = document.createElement('td');
        tdPod.className = 'pod-cell';
        const podDiv = document.createElement('div');
        podDiv.className = 'pod-name';
        podDiv.textContent = analysis.podName;
        tdPod.appendChild(podDiv);

        // Issue title cell
        const tdIssue = document.createElement('td');
        tdIssue.className = 'issue-title';
        const issueDiv = document.createElement('div');
        issueDiv.className = 'issue-text';
        issueDiv.textContent = issueTitle;
        tdIssue.appendChild(issueDiv);

        // Actions cell
        const tdActions = document.createElement('td');
        tdActions.className = 'actions-cell';
        const link = document.createElement('a');
        link.href = '/dashboard/analysis/' + encodeURIComponent(analysis.sessionId);
        link.className = 'btn btn-small btn-view';
        link.textContent = 'View Details';
        tdActions.appendChild(link);

        row.appendChild(tdTimestamp);
        row.appendChild(tdNamespace);
        row.appendChild(tdPod);
        row.appendChild(tdIssue);
        row.appendChild(tdActions);
        
        tbody.appendChild(row);
    });
    
    dashboardTableLoading = false;

    // Destroy existing DataTable instance if present (safety net)
    if ($.fn.DataTable.isDataTable('#dashboardTable')) {
        $('#dashboardTable').DataTable().destroy();
    }

    // Initialize DataTable
    $('#dashboardTable').DataTable({
        pageLength: 25,
        lengthMenu: [[10, 25, 50, 100], [10, 25, 50, 100]],
        order: [[0, 'desc']], // Sort by timestamp descending
        columnDefs: [
            {
                targets: 0, // Timestamp column
                type: 'date'
            },
            {
                targets: -1, // Actions column
                orderable: false,
                searchable: false
            }
        ],
        language: {
            search: "Search:",
            lengthMenu: "Show _MENU_ entries",
            info: "Showing _START_ to _END_ of _TOTAL_ issues",
            emptyTable: "No issues found"
        },
        responsive: true,
        autoWidth: false
    });
    
    console.log(`Loaded ${analyses.length} unhealthy analyses to dashboard`);
}

/**
 * Load workloads data
 */
function loadWorkloads() {
    console.log('Loading workloads...');
    
    // Prevent concurrent or duplicate initialization
    if (workloadsTableLoading || $.fn.DataTable.isDataTable('#workloadsTable')) {
        console.log('Workloads table already initialized or loading');
        return;
    }
    workloadsTableLoading = true;

    // Fetch workloads from API
    fetch('/api/workloads')
        .then(response => {
            if (!response.ok) {
                throw new Error('Failed to fetch workloads');
            }
            return response.json();
        })
        .then(data => {
            populateWorkloadsTable(data);
        })
        .catch(error => {
            workloadsTableLoading = false;
            console.error('Error loading workloads:', error);
            showWorkloadsError('Failed to load workloads. Please try again.');
        });
}

/**
 * Populate workloads table with data
 */
function populateWorkloadsTable(workloads) {
    const tbody = document.getElementById('workloadsTableBody');
    
    if (!workloads || workloads.length === 0) {
        const emptyRow = document.createElement('tr');
        const emptyTd = document.createElement('td');
        emptyTd.colSpan = 3;
        emptyTd.className = 'text-center';
        const emptyState = document.createElement('div');
        emptyState.className = 'empty-state';
        const emptyIcon = document.createElement('div');
        emptyIcon.className = 'empty-icon';
        emptyIcon.textContent = '📦';
        const emptyTitle = document.createElement('h3');
        emptyTitle.textContent = 'No Workloads Found';
        const emptyMsg = document.createElement('p');
        emptyMsg.textContent = 'No pods with RCA labels were found in the cluster.';
        emptyState.appendChild(emptyIcon);
        emptyState.appendChild(emptyTitle);
        emptyState.appendChild(emptyMsg);
        emptyTd.appendChild(emptyState);
        emptyRow.appendChild(emptyTd);
        tbody.appendChild(emptyRow);
        return;
    }
    
    tbody.innerHTML = '';
    
    workloads.forEach(workload => {
        const row = document.createElement('tr');
        row.className = 'analysis-row';

        // Namespace cell
        const tdNamespace = document.createElement('td');
        tdNamespace.className = 'namespace';
        tdNamespace.textContent = workload.namespace;

        // Pod cell
        const tdPod = document.createElement('td');
        tdPod.className = 'pod-cell';
        const podDiv = document.createElement('div');
        podDiv.className = 'pod-name';
        podDiv.textContent = workload.podName;
        tdPod.appendChild(podDiv);

        // Actions cell
        const tdActions = document.createElement('td');
        tdActions.className = 'actions-cell';
        const btn = document.createElement('button');
        btn.className = 'btn btn-small btn-analyze';
        btn.textContent = 'Trigger Analysis';
        btn.addEventListener('click', () => analyzePod(workload.namespace, workload.podName));
        tdActions.appendChild(btn);

        row.appendChild(tdNamespace);
        row.appendChild(tdPod);
        row.appendChild(tdActions);
        
        tbody.appendChild(row);
    });
    
    workloadsTableLoading = false;

    // Destroy existing DataTable instance if present (safety net)
    if ($.fn.DataTable.isDataTable('#workloadsTable')) {
        $('#workloadsTable').DataTable().destroy();
    }

    // Initialize DataTable
    $('#workloadsTable').DataTable({
        pageLength: 25,
        order: [[0, 'asc']],
        columnDefs: [
            {
                targets: -1, // Actions column
                orderable: false,
                searchable: false
            }
        ],
        language: {
            search: "Search:",
            lengthMenu: "Show _MENU_ entries",
            info: "Showing _START_ to _END_ of _TOTAL_ workloads",
            emptyTable: "No workloads available"
        },
        responsive: true,
        autoWidth: false
    });
    
    console.log(`Loaded ${workloads.length} workloads`);
}

/**
 * Show error message in workloads table
 */
function showWorkloadsError(message) {
    const tbody = document.getElementById('workloadsTableBody');
    tbody.innerHTML = '';
    const row = document.createElement('tr');
    const td = document.createElement('td');
    td.colSpan = 3;
    td.className = 'text-center';
    const errDiv = document.createElement('div');
    errDiv.className = 'error-message';
    const iconSpan = document.createElement('span');
    iconSpan.textContent = '⚠';
    const msgSpan = document.createElement('span');
    msgSpan.textContent = message;
    errDiv.appendChild(iconSpan);
    errDiv.appendChild(msgSpan);
    td.appendChild(errDiv);
    row.appendChild(td);
    tbody.appendChild(row);
}

/**
 * Analyze a specific pod
 */
function analyzePod(namespace, podName) {
    console.log(`Analyzing pod: ${namespace}/${podName}`);
    
    if (!confirm(`Trigger RCA analysis for pod ${podName} in namespace ${namespace}?`)) {
        return;
    }
    
    // Show loading state on button
    const button = event.target;
    const originalChildren = Array.from(button.childNodes).map(n => n.cloneNode(true));
    button.disabled = true;
    button.textContent = '';
    const spinSpan = document.createElement('span');
    spinSpan.textContent = '⟳';
    const labelSpan = document.createElement('span');
    labelSpan.textContent = 'Analyzing...';
    button.appendChild(spinSpan);
    button.appendChild(labelSpan);
    
    // Call RCA analyze API with query parameters
    fetch(`/rca/analyze?namespace=${encodeURIComponent(namespace)}&pod=${encodeURIComponent(podName)}`, {
        method: 'GET',
        headers: {
            'Accept': 'application/json'
        }
    })
    .then(response => {
        if (!response.ok) {
            throw new Error('Failed to trigger analysis');
        }
        return response.json();
    })
    .then(data => {
        console.log('Analysis triggered:', data);
        
        // Redirect to analysis details page with session ID
        if (data.sessionId) {
            window.location.href = `/dashboard/analysis/${data.sessionId}`;
        } else {
            alert('Analysis started but no session ID returned');
            window.location.href = '/dashboard';
        }
    })
    .catch(error => {
        console.error('Error triggering analysis:', error);
        alert('Failed to trigger analysis. Please try again.');
        button.disabled = false;
        button.textContent = '';
        originalChildren.forEach(n => button.appendChild(n));
    });
}

/**
 * Utility function to format timestamps
 */
function formatTimestamp(isoString) {
    const date = new Date(isoString);
    return date.toLocaleString();
}

/**
 * Utility function to calculate time ago
 */
function timeAgo(isoString) {
    const date = new Date(isoString);
    const now = new Date();
    const seconds = Math.floor((now - date) / 1000);
    
    if (seconds < 60) return `${seconds}s ago`;
    if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
    if (seconds < 86400) return `${Math.floor(seconds / 3600)}h ago`;
    return `${Math.floor(seconds / 86400)}d ago`;
}

// Export for use in templates
window.formatTimestamp = formatTimestamp;
window.timeAgo = timeAgo;
window.analyzePod = analyzePod;

// ============================================================
// CHATBOT WIDGET
// ============================================================

/**
 * Chatbot state
 */
const chatbot = {
    isOpen: false,
    // Holds the sessionId of the most recently viewed analysis (set when user opens a View Details page)
    // Falls back to the latest unhealthy analysis fetched from the dashboard API.
    currentSessionId: null,
    currentPodName:   null,
    currentNamespace: null
};

/**
 * Initialize chatbot on DOM ready
 */
$(document).ready(function () {
    initChatbot();
});

/**
 * Wire up chatbot toggle / close buttons
 */
function initChatbot() {
    const toggleBtn = document.getElementById('chatbotToggle');
    const closeBtn  = document.getElementById('chatbotClose');

    if (toggleBtn) toggleBtn.addEventListener('click', toggleChatbot);
    if (closeBtn)  closeBtn.addEventListener('click',  closeChatbot);

    // Resolve a session context from the page (dashboard table may already be loaded)
    resolveChatbotSession();
}

/**
 * Try to pick up a session ID from the current page context.
 * On the analysis-details page, reads from metadata element or URL.
 * On the dashboard we use the first unhealthy analysis.
 */
function resolveChatbotSession() {
    // Analysis details page: check for metadata element first
    const metaEl = document.getElementById('analysisPageMeta');
    if (metaEl) {
        chatbot.currentSessionId = metaEl.dataset.sessionId || null;
        chatbot.currentPodName   = metaEl.dataset.podName   || null;
        chatbot.currentNamespace = metaEl.dataset.namespace || null;
        console.log('Chatbot session resolved from metadata:', chatbot.currentSessionId);
        updatePodContextBar();
        return;
    }
    
    // Fallback: try to extract from URL
    const match = window.location.pathname.match(/\/dashboard\/analysis\/([^/]+)/);
    if (match) {
        chatbot.currentSessionId = match[1];
        console.log('Chatbot session resolved from URL:', chatbot.currentSessionId);
        updatePodContextBar();
    }
}

/**
 * Toggle chatbot panel open / closed
 */
function toggleChatbot() {
    if (chatbot.isOpen) {
        closeChatbot();
    } else {
        openChatbot();
    }
}

function openChatbot() {
    const panel     = document.getElementById('chatbotPanel');
    const toggleBtn = document.getElementById('chatbotToggle');
    if (panel)     panel.style.display = 'flex';
    if (toggleBtn) toggleBtn.style.display = 'none';   // hide toggle while panel is open
    chatbot.isOpen = true;
    updatePodContextBar();
}

function closeChatbot() {
    const panel     = document.getElementById('chatbotPanel');
    const toggleBtn = document.getElementById('chatbotToggle');
    if (panel)     panel.style.display = 'none';
    if (toggleBtn) toggleBtn.style.display = 'flex';   // show toggle again
    chatbot.isOpen = false;
}

/**
 * Returns true when we are on the main dashboard page (not an analysis-details page).
 * On the dashboard the user should always pick a pod from the list rather than
 * silently reusing a previously-cached session.
 */
function isOnDashboardPage() {
    return !window.location.pathname.match(/\/dashboard\/analysis\/([^/]+)/);
}

/**
 * Send a free-text message from the input box
 */
function sendChatMessage() {
    const input = document.getElementById('chatbotInput');
    if (!input) return;

    const text = input.value.trim();
    if (!text) return;

    input.value = '';
    appendUserMessage(text);
    showTypingIndicator();

    // Route free-text to the /api/chat endpoint
    fetch('/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            message: text,
            sessionId: chatbot.currentSessionId || null
        })
    })
    .then(r => {
        if (r.ok) return r.json();
        // Capture the HTTP status so the catch handler can give a precise message
        const err = new Error('HTTP ' + r.status);
        err.status = r.status;
        return Promise.reject(err);
    })
    .then(data => {
        removeTypingIndicator();
        appendBotMessage(data.reply || data.message || 'No response received.');
    })
    .catch(err => {
        removeTypingIndicator();
        if (err instanceof TypeError) {
            // Network-level failure (DNS, connection refused, etc.)
            appendBotMessage('⚠️ Could not connect to the CAUSA service. Please check your network and try again.');
        } else if (err.status === 404 || err.status === 405) {
            // Endpoint not implemented yet — guide the user to quick actions
            appendBotMessage(
                '💬 Free-text chat is not yet available. ' +
                'Please use the **Quick Actions** buttons below (Explain Root Cause, Immediate Mitigation, etc.) ' +
                'to interact with CAUSA AI.'
            );
        } else if (err.status >= 500) {
            appendBotMessage('⚠️ The CAUSA service returned an error (' + err.status + '). Please try again in a moment.');
        } else {
            appendBotMessage('⚠️ Unexpected response from the CAUSA service (' + (err.status || err.message) + '). Please try again.');
        }
    });
}

/**
 * Handle one of the four quick-action buttons
 * @param {'explain-root-cause'|'immediate-mitigation'|'permanent-fix'|'draft-incident-ticket'} action
 */
function chatQuickAction(action) {
    const labels = {
        'explain-root-cause':    '🔍 Explain Root Cause',
        'immediate-mitigation':  '⚡ Immediate Mitigation',
        'permanent-fix':         '💡 Recommended Solution',
        'draft-incident-ticket': '📋 Draft Incident Ticket'
    };

    appendUserMessage(labels[action] || action);

    // If a pod context is already set (from a previous quick action or from the
    // analysis-details page URL), reuse it directly — no need to show the picker again.
    // The user can always switch pods via the "Change Pod" button in the chat header.
    if (chatbot.currentSessionId) {
        showTypingIndicator();
        runChatAction(action, chatbot.currentSessionId);
        return;
    }

    // No pod context yet — show the pod picker so the user can choose which analysis to query.
    showPodPicker(action);
}

/**
 * Collect the minimal session summaries needed for the pod picker.
 * First tries to read from the already-loaded dashboard table rows (zero extra
 * network round-trip).  Falls back to the existing /api/analyses endpoint.
 */
function collectSessionSummaries() {
    // 1. Try to read from the dashboard table that is already in the DOM.
    const rows = document.querySelectorAll('#dashboardTableBody tr.analysis-row');
    if (rows && rows.length > 0) {
        const sessions = [];
        rows.forEach(row => {
            const link = row.querySelector('a.btn-view');
            if (!link) return;
            const href = link.getAttribute('href') || '';
            const m = href.match(/\/dashboard\/analysis\/([^/]+)/);
            if (!m) return;
            const cells = row.querySelectorAll('td');
            // pod name may be wrapped in a <div class="pod-name"> inside the cell
            const podCell = cells[2];
            const podNameEl = podCell ? podCell.querySelector('.pod-name') : null;
            sessions.push({
                sessionId: m[1],
                namespace:  cells[1] ? cells[1].textContent.trim() : '',
                podName:    podNameEl ? podNameEl.textContent.trim() : (podCell ? podCell.textContent.trim() : ''),
                status:     'COMPLETED'
            });
        });
        if (sessions.length > 0) return Promise.resolve(sessions);
    }

    // 2. Fall back to the analyses API endpoint (same source loadDashboard uses).
    //    Use AbortController so the pod-picker never hangs indefinitely.
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 10000);

    return fetch('/api/analyses/unhealthy?pageSize=100', { signal: controller.signal })
        .then(r => {
            clearTimeout(timeoutId);
            return r.ok ? r.json() : Promise.reject(r);
        })
        .then(data => {
            const list = Array.isArray(data) ? data : (data.analyses || []);
            return list.filter(s => s.sessionId);
        })
        .catch(err => {
            clearTimeout(timeoutId);
            // If the unhealthy endpoint fails, try the general analyses endpoint as last resort.
            if (err && err.name === 'AbortError') {
                return Promise.reject(new Error('Request timed out while loading analysed pods.'));
            }
            return fetch('/api/analyses?pageSize=100')
                .then(r => r.ok ? r.json() : Promise.reject(r))
                .then(data => {
                    const list = Array.isArray(data) ? data : (data.analyses || []);
                    return list.filter(s => s.sessionId);
                });
        });
}

/**
 * Fetch all completed analyses and render a selectable pod list in the chat.
 * Once the user picks a pod, runChatAction() is called with that session.
 */
function showPodPicker(action) {
    const container = document.getElementById('chatbotMessages');
    if (!container) return;

    // Use a unique ID per invocation so multiple concurrent pod-pickers
    // (one per quick-action click) each resolve independently.
    const pickerId = 'chatPodPickerMsg_' + Date.now();

    // Render a loading placeholder immediately
    const loadingWrapper = document.createElement('div');
    loadingWrapper.className = 'chat-message bot-message';
    loadingWrapper.id = pickerId;
    const loadingBubble = document.createElement('div');
    loadingBubble.className = 'message-bubble';
    const loadingSpan = document.createElement('span');
    loadingSpan.className = 'pod-picker-loading';
    loadingSpan.textContent = '⏳ Loading analysed pods…';
    loadingBubble.appendChild(loadingSpan);
    loadingWrapper.appendChild(loadingBubble);
    container.appendChild(loadingWrapper);
    container.scrollTop = container.scrollHeight;

    collectSessionSummaries()
        .then(sessions => {

            const msgEl = document.getElementById(pickerId);
            if (!msgEl) return;

            if (!sessions.length) {
                const noAnalysisBubble = document.createElement('div');
                noAnalysisBubble.className = 'message-bubble';
                noAnalysisBubble.textContent = '⚠️ No analyses found. Trigger an analysis from the ';
                const strongEl = document.createElement('strong');
                strongEl.textContent = 'Workloads';
                noAnalysisBubble.appendChild(strongEl);
                const suffixText = document.createTextNode(' tab first.');
                noAnalysisBubble.appendChild(suffixText);
                msgEl.textContent = '';
                msgEl.appendChild(noAnalysisBubble);
                return;
            }

            // Build picker UI
            const bubble = document.createElement('div');
            bubble.className = 'message-bubble';

            const intro = document.createElement('p');
            intro.style.cssText = 'margin:0 0 8px;font-size:13px;font-weight:600;';
            intro.textContent = '🔎 Select a pod to analyse:';
            bubble.appendChild(intro);

            const list = document.createElement('div');
            list.className = 'pod-picker';

            sessions.forEach(session => {
                const statusIcon = podStatusIcon(session.status);
                const btn = document.createElement('button');
                btn.className = 'pod-picker-item';

                // Build button content using DOM API to avoid XSS
                const nameSpan = document.createElement('span');
                const strong = document.createElement('strong');
                strong.textContent = session.podName || session.sessionId;
                nameSpan.textContent = statusIcon + ' ';
                nameSpan.appendChild(strong);

                const nsSpan = document.createElement('span');
                nsSpan.className = 'pod-picker-ns';
                nsSpan.textContent = (session.namespace || '') + ' · ' + (session.status || '');

                btn.appendChild(nameSpan);
                btn.appendChild(nsSpan);

                btn.addEventListener('click', () => {
                    // Highlight selection
                    list.querySelectorAll('.pod-picker-item').forEach(b => b.style.opacity = '0.5');
                    btn.style.opacity = '1';
                    btn.style.background = '#dde8ff';
                    btn.style.borderColor = 'var(--color-primary, #0f62fe)';

                    // Set session context, update context bar, and run action
                    chatbot.currentSessionId = session.sessionId;
                    chatbot.currentPodName   = session.podName || session.sessionId;
                    chatbot.currentNamespace = session.namespace || '';
                    updatePodContextBar();

                    appendUserMessage(`📌 Selected: ${session.podName || session.sessionId}`);
                    showTypingIndicator();
                    runChatAction(action, session.sessionId);
                });
                list.appendChild(btn);
            });

            bubble.appendChild(list);
            msgEl.innerHTML = '';
            msgEl.appendChild(bubble);
            container.scrollTop = container.scrollHeight;
        })
        .catch(err => {
            const msgEl = document.getElementById(pickerId);
            if (msgEl) {
                const msg = (err && err.message && err.message.includes('timed out'))
                    ? '⚠️ Request timed out while loading analysed pods. Please try again.'
                    : '⚠️ Could not load analyses. Please check your connection and try again.';
                msgEl.innerHTML = '';
                const errBubble = document.createElement('div');
                errBubble.className = 'message-bubble';
                errBubble.textContent = msg;
                msgEl.appendChild(errBubble);
            }
        });
}

/**
 * Show/hide the pod context bar and "Change Pod" button based on chatbot state.
 */
function updatePodContextBar() {
    const contextBar  = document.getElementById('chatbotPodContext');
    const podLabel    = document.getElementById('chatbotPodLabel');
    const changePodBtn = document.getElementById('chatbotChangePod');
    
    // Check if we're on the analysis details page (pod is fixed, can't change)
    const isAnalysisPage = !!document.getElementById('analysisPageMeta');

    if (chatbot.currentSessionId && chatbot.currentPodName) {
        const ns = chatbot.currentNamespace ? ` · ${chatbot.currentNamespace}` : '';
        if (podLabel)    podLabel.textContent = `${chatbot.currentPodName}${ns}`;
        if (contextBar)  contextBar.style.display = 'flex';
        // Hide "Change Pod" button on analysis details page
        if (changePodBtn) changePodBtn.style.display = isAnalysisPage ? 'none' : 'inline-flex';
    } else {
        if (contextBar)  contextBar.style.display = 'none';
        if (changePodBtn) changePodBtn.style.display = 'none';
    }
}

/**
 * Reset the pod context so the user can pick a different pod.
 * Called by the "Change Pod" button in the chat header.
 */
function resetPodContext() {
    chatbot.currentSessionId = null;
    chatbot.currentPodName   = null;
    chatbot.currentNamespace = null;
    updatePodContextBar();
    appendBotMessage('🔄 Pod context cleared. Click a quick action to select a different pod.');
}

/**
 * Execute a quick action against a known sessionId.
 * Tries /api/chat/action first; falls back to direct analysis fetch.
 */
function runChatAction(action, sessionId) {
    fetch('/api/chat/action', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action, sessionId })
    })
    .then(r => r.ok ? r.json() : Promise.reject(r))
    .then(data => {
        removeTypingIndicator();
        handleActionResponse(action, data);
    })
    .catch(() => {
        removeTypingIndicator();
        handleActionFallback(action, sessionId);
    });
}

/**
 * Render the structured response for each action type.
 * Falls back gracefully when the backend is not yet wired.
 */
function handleActionResponse(action, data) {
    switch (action) {
        case 'explain-root-cause':
            appendBotMessage(data.explanation || data.reply || formatMoreInfo(data));
            break;
        case 'immediate-mitigation':
            appendBotMessage(data.steps ? formatSolutionList('⚡ Immediate Mitigation Steps', data.steps) : (data.reply || '⚡ No mitigation steps available.'));
            break;
        case 'permanent-fix':
            appendBotMessage('💡 **Recommended Solution**\n\n🚧 Stay tuned! This feature is coming soon. We\'re working on providing automated recommended solutions based on your analysis.');
            break;
        case 'draft-incident-ticket':
            appendBotMessage(data.ticket ? formatJiraTicket(data.ticket) : (data.reply || '📋 Ticket data not available.'));
            break;
        default:
            appendBotMessage(data.reply || JSON.stringify(data));
    }
}

/**
 * Graceful fallback when the /api/chat/action endpoint is not yet available.
 * Pulls data directly from the existing /api/analyses/{sessionId} endpoint.
 */
function handleActionFallback(action, sessionId) {
    if (!sessionId) {
        appendBotMessage('⚠️ No analysis session selected. Please pick a pod first.');
        return;
    }

    switch (action) {
        case 'explain-root-cause':
            fetchAnalysisAndRespond(sessionId, (report, session) => {
                const lines = [];
                lines.push(`**Pod:** \`${session.podName || 'N/A'}\` in namespace \`${session.namespace || 'N/A'}\``);
                if (report.title) lines.push(`**Title:** ${report.title}`);
                if (report.issue) {
                    lines.push('');
                    lines.push(`**Root Cause / Issue:**\n${report.issue}`);
                }
                if (report.evidence) {
                    lines.push('');
                    lines.push(`**Supporting Evidence:**\n${report.evidence}`);
                }
                if (report.validationConfidence != null) {
                    lines.push('');
                    lines.push(`**Confidence:** ${Math.round(report.validationConfidence * 100)}%`);
                }
                if (report.validationNotes) {
                    lines.push('');
                    lines.push(`**Validation Notes:**\n${report.validationNotes}`);
                }
                appendBotMessage(lines.length > 1 ? lines.join('\n') : '📄 Root cause details not yet available for this analysis.');
            });
            break;

        case 'immediate-mitigation':
            fetchAnalysisAndRespond(sessionId, (report, session) => {
                const steps = extractSolutions(report, 'temporary');
                const header = `**Workload:** \`${session.podName || 'N/A'}\` · \`${session.namespace || 'N/A'}\`\n`;
                appendBotMessage(header + formatSolutionList('⚡ Immediate Mitigation Steps', steps));
            });
            break;

        case 'permanent-fix':
            appendBotMessage('💡 **Recommended Solution**\n\n🚧 Stay tuned! This feature is coming soon. We\'re working on providing automated recommended solutions based on your analysis.');
            break;

        case 'draft-incident-ticket':
            fetchAnalysisAndRespond(sessionId, (report, session) => {
                const ticket = buildJiraTicket(report, session);
                appendBotMessage(formatJiraTicket(ticket));
            });
            break;
    }
}

/**
 * Fetch analysis session data and invoke callback with (report, session).
 * Uses a 15-second AbortController timeout so the typing indicator never
 * spins forever when the backend is slow or unreachable.
 */
function fetchAnalysisAndRespond(sessionId, callback) {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 15000);

    fetch(`/api/analyses/${sessionId}`, { signal: controller.signal })
        .then(r => {
            clearTimeout(timeoutId);
            return r.ok ? r.json() : Promise.reject(r);
        })
        .then(session => {
            const report = session.report || {};
            callback(report, session);
        })
        .catch(err => {
            clearTimeout(timeoutId);
            removeTypingIndicator();
            if (err && err.name === 'AbortError') {
                appendBotMessage('⚠️ Request timed out while retrieving analysis data. Please try again.');
            } else {
                appendBotMessage('⚠️ Could not retrieve analysis data. Please check the analysis details page directly.');
            }
        });
}

/**
 * Build a JIRA-style ticket object from report + session data.
 * Maps to actual RcaReport fields: title, issue, evidence, supportedLogs,
 * validationConfidence, validationTrace
 */
function buildJiraTicket(report, session) {
    const logLines = (report.supportedLogs && report.supportedLogs.length)
        ? report.supportedLogs.map(l => `  - ${l}`).join('\n')
        : '  N/A';

    // Format validation trace items
    let validationTraceLines = '  N/A';
    if (report.validationTrace && report.validationTrace.length) {
        validationTraceLines = report.validationTrace.map(item => {
            const parts = [];
            if (item.claim) parts.push(`*Claim:* ${item.claim}`);
            if (item.decision) parts.push(`*Decision:* ${item.decision}`);
            if (item.confidence != null) parts.push(`*Confidence:* ${Math.round(item.confidence * 100)}%`);
            if (item.notes) parts.push(`*Notes:* ${item.notes}`);
            if (item.evidenceFound && item.evidenceFound.length) {
                parts.push(`*Evidence:* ${item.evidenceFound.join(', ')}`);
            }
            return '  • ' + parts.join(' | ');
        }).join('\n');
    }

    const confidence = report.validationConfidence != null
        ? `${Math.round(report.validationConfidence * 100)}%`
        : 'N/A';

    return {
        summary:   report.title || 'Kubernetes Issue Detected',
        type:      'Bug',
        priority:  'High',
        component: session.namespace || 'kubernetes',
        labels:    ['causa', 'rca', 'kubernetes'].filter(Boolean),
        description: [
            `*Pod:* ${session.podName || 'N/A'}`,
            `*Namespace:* ${session.namespace || 'N/A'}`,
            `*Analysis Session:* ${session.sessionId || 'N/A'}`,
            `*Timestamp:* ${session.timestamp ? new Date(session.timestamp).toLocaleString() : 'N/A'}`,
            `*Confidence:* ${confidence}`,
            '',
            `*Issue Description:*`,
            report.issue || 'See analysis details.',
            '',
            `*Evidence:*`,
            report.evidence || 'See analysis details.',
            '',
            `*Supported Logs:*`,
            logLines,
            '',
            `*Validation Trace:*`,
            validationTraceLines
        ].join('\n')
    };
}

/**
 * Extract actionable items from a report object.
 *
 * RcaReport fields available:
 *   title, issue, evidence, supportedLogs,
 *   validationConfidence, validationNotes, validationChecklist
 *
 * For "temporary" (immediate mitigation):
 *   - validationChecklist items are actionable yes/no checks → surface as steps
 *   - validationNotes often contains immediate guidance
 *
 * For "permanent" (permanent fix):
 *   - The issue description + evidence together describe what needs to be fixed
 *   - validationNotes may contain longer-term guidance
 */
function extractSolutions(report, type) {
    if (type === 'temporary') {
        const steps = [];

        // Primary source: validationNotes contains free-text guidance from the
        // validation agent and is the most actionable source for immediate steps.
        if (report.validationNotes && report.validationNotes.trim()) {
            const lines = report.validationNotes.split('\n').map(l => l.trim()).filter(Boolean);
            lines.forEach(l => steps.push(l));
        }

        // Secondary: derive a step from the issue description if notes are absent.
        if (!steps.length && report.issue && report.issue.trim()) {
            steps.push(`Investigate and address the identified issue: ${report.issue}`);
        }

        // Tertiary: surface the evidence so the operator knows where to look.
        if (!steps.length && report.evidence && report.evidence.trim()) {
            steps.push(`Review the following evidence: ${report.evidence}`);
        }

        // Append validation checklist as supporting context (not primary steps).
        // These are Yes/No checks (e.g. "Metrics confirm OOM: Yes") that help
        // the operator verify the issue but are not actionable steps themselves.
        if (report.validationChecklist && report.validationChecklist.length) {
            steps.push('**Validation checks (supporting context):**');
            report.validationChecklist.forEach(item => steps.push(`  • ${item}`));
        }

        if (steps.length) return steps;

        return ['No immediate mitigation steps found in this analysis report.'];
    }

    // permanent fix
    const fixes = [];

    // The issue field describes what broke — fixing it IS the permanent fix.
    if (report.issue && report.issue.trim()) {
        fixes.push(`**Root Issue to Fix:** ${report.issue}`);
    }

    // Evidence tells you what to look at.
    if (report.evidence && report.evidence.trim()) {
        fixes.push(`**Evidence to Address:** ${report.evidence}`);
    }

    // validationNotes may contain longer-term recommendations.
    if (report.validationNotes && report.validationNotes.trim()) {
        const lines = report.validationNotes.split('\n').map(l => l.trim()).filter(Boolean);
        lines.forEach(l => fixes.push(l));
    }

    // Validation checklist as supporting context.
    if (report.validationChecklist && report.validationChecklist.length) {
        fixes.push('**Validation checks (supporting context):**');
        report.validationChecklist.forEach(item => fixes.push(`  • ${item}`));
    }

    if (fixes.length) return fixes;

    return ['No permanent fix details found in this analysis report.'];
}

// ---- Formatting helpers ----

function formatMoreInfo(data) {
    const parts = [];
    if (data.anomalyType) parts.push(`**Anomaly:** ${data.anomalyType}`);
    if (data.title)       parts.push(`**Issue:** ${data.title}`);
    if (data.description) parts.push(`**Description:** ${data.description}`);
    return parts.join('\n') || JSON.stringify(data);
}

function formatJiraTicket(ticket) {
    return [
        '📋 **JIRA Ticket Draft**',
        '',
        `**Summary:** ${ticket.summary}`,
        `**Type:** ${ticket.type || 'Bug'}`,
        `**Priority:** ${ticket.priority || 'High'}`,
        `**Component:** ${ticket.component || 'kubernetes'}`,
        `**Labels:** ${(ticket.labels || []).join(', ')}`,
        '',
        '**Description:**',
        '```',
        ticket.description || '',
        '```'
    ].join('\n');
}

function formatSolutionList(heading, solutions) {
    if (!solutions || solutions.length === 0) {
        return `${heading}\n\nNo solutions available.`;
    }
    const items = solutions.map((s, i) => `${i + 1}. ${s}`).join('\n');
    return `${heading}\n\n${items}`;
}

/**
 * Get the session ID of the first row in the dashboard table (if loaded).
 * Kept for backward-compat but no longer used by chatQuickAction.
 */
function getLatestDashboardSessionId() {
    const firstLink = document.querySelector('#dashboardTableBody .btn-view');
    if (firstLink) {
        const href = firstLink.getAttribute('href') || '';
        const m = href.match(/\/dashboard\/analysis\/([^/]+)/);
        if (m) return m[1];
    }
    return null;
}

/**
 * Escape HTML special characters to prevent XSS in text content
 */
function escapeHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

/**
 * Return an emoji status icon for a given AnalysisStatus string
 */
function podStatusIcon(status) {
    switch ((status || '').toUpperCase()) {
        case 'COMPLETED':         return '✅';
        case 'HEALTHY':           return '💚';
        case 'FAILED':            return '❌';
        case 'INITIATED':         return '🕐';
        case 'COLLECTING_DATA':   return '📡';
        case 'DETECTING_ANOMALY': return '🔬';
        case 'ANALYZING_RCA':     return '🧠';
        case 'VALIDATING':        return '🔍';
        default:                  return '⚪';
    }
}

// ---- DOM helpers ----

function appendUserMessage(text) {
    appendMessage(text, 'user-message');
}

function appendBotMessage(text) {
    appendMessage(text, 'bot-message');
}

function appendMessage(text, cssClass) {
    const container = document.getElementById('chatbotMessages');
    if (!container) return;

    const wrapper = document.createElement('div');
    wrapper.className = `chat-message ${cssClass}`;

    const bubble = document.createElement('div');
    bubble.className = 'message-bubble';

    // User messages are rendered as plain text to prevent XSS from keyboard input.
    // Bot messages go through markdownToFragment which builds a DocumentFragment
    // entirely via DOM API — no innerHTML or HTML string assignment is used.
    if (cssClass === 'user-message') {
        bubble.textContent = text;
    } else {
        bubble.appendChild(markdownToFragment(text));
    }

    wrapper.appendChild(bubble);
    container.appendChild(wrapper);
    container.scrollTop = container.scrollHeight;
}

function showTypingIndicator() {
    const container = document.getElementById('chatbotMessages');
    if (!container) return;

    const wrapper = document.createElement('div');
    wrapper.className = 'chat-message bot-message typing-indicator';
    wrapper.id = 'chatTypingIndicator';

    const bubble = document.createElement('div');
    bubble.className = 'message-bubble';
    for (let i = 0; i < 3; i++) {
        const dot = document.createElement('span');
        dot.className = 'typing-dot';
        bubble.appendChild(dot);
    }

    wrapper.appendChild(bubble);
    container.appendChild(wrapper);
    container.scrollTop = container.scrollHeight;
}

function removeTypingIndicator() {
    const el = document.getElementById('chatTypingIndicator');
    if (el) el.remove();
}

/**
 * Minimal markdown → DocumentFragment converter for chat messages.
 * Supports: **bold**, `code`, ```code blocks```, numbered/bullet lists, newlines.
 * All text content is set via textContent — no innerHTML is used anywhere.
 */
function markdownToFragment(text) {
    const fragment = document.createDocumentFragment();
    if (!text) return fragment;

    // Split on code blocks first to handle them separately
    const codeBlockRe = /```([\s\S]*?)```/g;
    let lastIndex = 0;
    let match;

    while ((match = codeBlockRe.exec(text)) !== null) {
        // Process inline content before this code block
        if (match.index > lastIndex) {
            appendInlineNodes(fragment, text.slice(lastIndex, match.index));
        }
        // Append <pre><code>...</code></pre> with textContent
        const pre = document.createElement('pre');
        const code = document.createElement('code');
        code.textContent = match[1].trim();
        pre.appendChild(code);
        fragment.appendChild(pre);
        lastIndex = codeBlockRe.lastIndex;
    }

    // Process any remaining text after the last code block
    if (lastIndex < text.length) {
        appendInlineNodes(fragment, text.slice(lastIndex));
    }

    return fragment;
}

/**
 * Parse a plain-text segment (no fenced code blocks) into DOM nodes supporting:
 * **bold**, `inline code`, numbered lists, bullet lists, and newlines → <br>.
 * Appends resulting nodes to the given parent.
 */
function appendInlineNodes(parent, text) {
    // Split into lines to detect list blocks
    const lines = text.split('\n');
    let i = 0;

    while (i < lines.length) {
        const line = lines[i];

        // Numbered list item: "1. text"
        if (/^\d+\.\s+/.test(line)) {
            const ol = document.createElement('ol');
            while (i < lines.length && /^\d+\.\s+/.test(lines[i])) {
                const li = document.createElement('li');
                appendStyledText(li, lines[i].replace(/^\d+\.\s+/, ''));
                ol.appendChild(li);
                i++;
            }
            parent.appendChild(ol);
            continue;
        }

        // Bullet list item: "- text" or "* text"
        if (/^[-*]\s+/.test(line)) {
            const ul = document.createElement('ul');
            while (i < lines.length && /^[-*]\s+/.test(lines[i])) {
                const li = document.createElement('li');
                appendStyledText(li, lines[i].replace(/^[-*]\s+/, ''));
                ul.appendChild(li);
                i++;
            }
            parent.appendChild(ul);
            continue;
        }

        // Regular line: append inline styled text + <br> for newline
        appendStyledText(parent, line);
        // Add <br> between lines (but not after the very last line)
        if (i < lines.length - 1) {
            parent.appendChild(document.createElement('br'));
        }
        i++;
    }
}

/**
 * Parse inline markdown (**bold** and `code`) in a plain-text segment
 * and append the resulting nodes to the given parent element.
 * All text is set via textContent — no innerHTML used.
 */
function appendStyledText(parent, text) {
    // Tokenise on **bold** and `inline code` markers
    const tokenRe = /(`[^`]+`|\*\*[^*]+\*\*)/g;
    let last = 0;
    let m;

    while ((m = tokenRe.exec(text)) !== null) {
        // Plain text before this token
        if (m.index > last) {
            parent.appendChild(document.createTextNode(text.slice(last, m.index)));
        }
        const token = m[0];
        if (token.startsWith('`')) {
            // Inline code
            const code = document.createElement('code');
            code.textContent = token.slice(1, -1);
            parent.appendChild(code);
        } else {
            // Bold
            const strong = document.createElement('strong');
            strong.textContent = token.slice(2, -2);
            parent.appendChild(strong);
        }
        last = tokenRe.lastIndex;
    }

    // Remaining plain text
    if (last < text.length) {
        parent.appendChild(document.createTextNode(text.slice(last)));
    }
}

// Expose to global scope for inline onclick handlers
window.sendChatMessage  = sendChatMessage;
window.chatQuickAction  = chatQuickAction;
window.toggleChatbot    = toggleChatbot;


