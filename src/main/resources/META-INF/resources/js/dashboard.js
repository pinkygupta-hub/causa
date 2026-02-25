/**
 * CAUSA RCA Dashboard - JavaScript
 * Handles DataTables initialization, tab switching, sidebar collapse, and workload scanning
 */

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
    
    // Setup auto-refresh for in-progress analyses
    setupAutoRefresh();
});

/**
 * Initialize tab switching functionality
 */
function initTabSwitching() {
    const tabButtons = document.querySelectorAll('.tab-button');
    const tabContents = document.querySelectorAll('.tab-content');
    
    tabButtons.forEach(button => {
        button.addEventListener('click', () => {
            const targetTab = button.getAttribute('data-tab');
            
            // Remove active class from all buttons and contents
            tabButtons.forEach(btn => btn.classList.remove('active'));
            tabContents.forEach(content => content.classList.remove('active'));
            
            // Add active class to clicked button and corresponding content
            button.classList.add('active');
            document.getElementById(`${targetTab}-tab`).classList.add('active');
            
            // Load data for specific tabs
            if (targetTab === 'dashboard') {
                loadDashboard();
            } else if (targetTab === 'workloads') {
                loadWorkloads();
            }
            
            console.log(`Switched to ${targetTab} tab`);
        });
    });
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
    
    // Check if dashboard table is already initialized
    if ($.fn.DataTable.isDataTable('#dashboardTable')) {
        console.log('Dashboard table already initialized');
        return;
    }
    
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
            console.error('Error loading dashboard:', error);
            if (loadingIndicator) {
                loadingIndicator.innerHTML = '<div class="error-message"><span>⚠</span><span>Failed to load dashboard data</span></div>';
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
        
        // Extract anomaly info from report - use whatever is returned
        const anomalyType = analysis.report?.anomalyType || 'Unknown';
        const issueTitle = analysis.report?.title || analysis.report?.issue || 'Issue detected';
        
        row.innerHTML = `
            <td class="timestamp-cell" data-order="${analysis.timestamp}">
                <div class="timestamp">${formatTimestamp(analysis.timestamp)}</div>
            </td>
            <td class="namespace">${analysis.namespace}</td>
            <td class="pod-cell">
                <div class="pod-name">${analysis.podName}</div>
            </td>
            <td class="anomaly-type">
                <span class="badge badge-warning">${anomalyType}</span>
            </td>
            <td class="issue-title">
                <div class="issue-text">${issueTitle}</div>
            </td>
            <td class="actions-cell">
                <a href="/dashboard/analysis/${analysis.sessionId}" class="btn btn-small btn-view">
                    View Details
                </a>
            </td>
        `;
        
        tbody.appendChild(row);
    });
    
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
    
    // Check if table is already initialized
    if ($.fn.DataTable.isDataTable('#workloadsTable')) {
        console.log('Workloads table already initialized');
        return;
    }
    
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
        tbody.innerHTML = `
            <tr>
                <td colspan="3" class="text-center">
                    <div class="empty-state">
                        <div class="empty-icon">📦</div>
                        <h3>No Workloads Found</h3>
                        <p>No pods with RCA labels were found in the cluster.</p>
                    </div>
                </td>
            </tr>
        `;
        return;
    }
    
    tbody.innerHTML = '';
    
    workloads.forEach(workload => {
        const row = document.createElement('tr');
        row.className = 'analysis-row';
        
        row.innerHTML = `
            <td class="namespace">${workload.namespace}</td>
            <td class="pod-cell">
                <div class="pod-name">${workload.podName}</div>
            </td>
            <td class="actions-cell">
                <button onclick="analyzePod('${workload.namespace}', '${workload.podName}')"
                        class="btn btn-small btn-analyze">
                    Trigger Analysis
                </button>
            </td>
        `;
        
        tbody.appendChild(row);
    });
    
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
    tbody.innerHTML = `
        <tr>
            <td colspan="3" class="text-center">
                <div class="error-message">
                    <span>⚠</span>
                    <span>${message}</span>
                </div>
            </td>
        </tr>
    `;
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
    const originalText = button.innerHTML;
    button.disabled = true;
    button.innerHTML = '<span>⟳</span><span>Analyzing...</span>';
    
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
        button.innerHTML = originalText;
    });
}

/**
 * Auto-refresh for in-progress analyses
 */
function setupAutoRefresh() {
    // Check if there are any in-progress analyses
    const inProgressElements = document.querySelectorAll('.status-in-progress');
    
    if (inProgressElements.length > 0) {
        console.log(`Found ${inProgressElements.length} in-progress analyses. Setting up auto-refresh...`);
        
        // Refresh every 10 seconds if there are in-progress analyses
        setTimeout(() => {
            console.log('Auto-refreshing dashboard...');
            window.location.reload();
        }, 10000);
    }
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

// Made with Bob
