const summaryEndpoint = '/api/logs/summary';
const recentEndpoint = '/api/logs/recent';

function formatJson(value) {
    return JSON.stringify(value, null, 2);
}

function updateSelect(id, values, defaultLabel) {
    const select = document.getElementById(id);
    const current = select.value || '';
    select.innerHTML = `<option value="">${defaultLabel}</option>`;
    values.forEach(value => {
        const option = document.createElement('option');
        option.value = value;
        option.textContent = value;
        select.appendChild(option);
    });
    if (values.includes(current)) {
        select.value = current;
    }
}

async function fetchSummary() {
    try {
        const response = await fetch(summaryEndpoint);
        if (!response.ok) {
            throw new Error('Error al obtener resumen');
        }
        const data = await response.json();

        document.getElementById('totalEvents').textContent = data.totalEvents;
        document.getElementById('levelCounts').textContent = formatJson(data.levels);
        document.getElementById('sourceCounts').textContent = formatJson(data.sources);

        updateSelect('filterSource', data.availableSources || [], 'Todas');
        updateSelect('filterLevel', data.availableLevels || [], 'Todos');

        const topSources = document.getElementById('topSources');
        if (topSources) {
            topSources.innerHTML = '';
            (data.topSources || []).forEach(entry => {
                const item = document.createElement('li');
                item.textContent = `${entry.label}: ${entry.count}`;
                topSources.appendChild(item);
            });
        }

        const topLevels = document.getElementById('topLevels');
        if (topLevels) {
            topLevels.innerHTML = '';
            (data.topLevels || []).forEach(entry => {
                const item = document.createElement('li');
                item.textContent = `${entry.label}: ${entry.count}`;
                topLevels.appendChild(item);
            });
        }

        const anomaliesList = document.getElementById('anomalies');
        anomaliesList.innerHTML = '';
        const anomalies = data.globalAnomalies || [];
        if (anomalies.length === 0) {
            const item = document.createElement('li');
            item.textContent = 'No se detectaron anomalías recientes.';
            anomaliesList.appendChild(item);
        } else {
            anomalies.slice(-20).reverse().forEach(alert => {
                const item = document.createElement('li');
                item.textContent = alert;
                anomaliesList.appendChild(item);
            });
        }
    } catch (error) {
        console.error('fetchSummary:', error);
    }
}

function buildQuery() {
    const source = document.getElementById('filterSource').value;
    const level = document.getElementById('filterLevel').value;
    const contains = document.getElementById('filterSearch').value;
    const minutes = document.getElementById('filterWindow').value;
    const params = new URLSearchParams();

    if (source) params.append('source', source);
    if (level) params.append('level', level);
    if (contains) params.append('contains', contains);
    if (minutes) params.append('minutes', minutes);
    params.append('limit', '100');
    return params.toString();
}

async function fetchFilteredLogs() {
    try {
        const query = buildQuery();
        const response = await fetch(`${recentEndpoint}?${query}`);
        if (!response.ok) {
            throw new Error('Error al obtener logs filtrados');
        }
        const data = await response.json();

        const recentTable = document.getElementById('recentLogs');
        recentTable.innerHTML = '';
        data.forEach(event => {
            const row = document.createElement('tr');
            row.innerHTML = `
                <td>${event.timestamp ? new Date(event.timestamp).toLocaleTimeString() : '-'}</td>
                <td>${event.source || '-'}</td>
                <td>${event.level || '-'}</td>
                <td>${event.message || '-'}</td>
            `;
            recentTable.appendChild(row);
        });

        document.getElementById('filteredCount').textContent = `${data.length} mensajes`;
    } catch (error) {
        console.error('fetchFilteredLogs:', error);
    }
}

function applyFilters() {
    fetchFilteredLogs();
}

async function uploadFile() {
    const fileInput = document.getElementById('uploadFile');
    const status = document.getElementById('uploadStatus');
    if (!fileInput.files || fileInput.files.length === 0) {
        status.textContent = 'Selecciona un archivo CSV o JSON para subir.';
        return;
    }

    const formData = new FormData();
    formData.append('file', fileInput.files[0]);
    status.textContent = 'Subiendo...';

    try {
        const response = await fetch('/api/logs/upload', {
            method: 'POST',
            body: formData
        });
        if (!response.ok) {
            throw new Error('Error al subir el archivo');
        }
        const data = await response.json();
        status.textContent = `Procesado ${data.ingested} registros. ` +
            (data.anomalies && data.anomalies.length ? `Anomalías detectadas: ${data.anomalies.length}` : 'Sin anomalías detectadas.');
        fetchSummary();
        fetchFilteredLogs();
    } catch (error) {
        console.error('uploadFile:', error);
        status.textContent = 'Error al procesar el archivo.';
    }
}

document.getElementById('applyFilters').addEventListener('click', applyFilters);
document.getElementById('uploadButton').addEventListener('click', uploadFile);

fetchSummary();
fetchFilteredLogs();
setInterval(() => {
    fetchSummary();
    fetchFilteredLogs();
}, 5000);
