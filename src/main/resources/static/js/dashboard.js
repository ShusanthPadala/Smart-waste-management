document.addEventListener('DOMContentLoaded', () => {
    // Chart instances
    let statusChart, fillChart, sustainabilityChart, trendChart;
    
    const refreshDataBtn = document.getElementById('refreshDataBtn');
    const generateRouteBtn = document.getElementById('generateRouteBtn');
    const trendBinSelector = document.getElementById('trendBinSelector');
    
    // Initialize Dashboard
    initDashboard();
    
    refreshDataBtn.addEventListener('click', () => {
        loadPredictions();
        if (trendBinSelector.value) {
            loadTrendData(trendBinSelector.value);
        }
    });
    
    generateRouteBtn.addEventListener('click', () => {
        generateOptimizedRoute();
    });
    
    trendBinSelector.addEventListener('change', (e) => {
        if (e.target.value) {
            loadTrendData(e.target.value);
        }
    });
    
    // CRUD Logic
    const binModal = new bootstrap.Modal(document.getElementById('binModal'));
    const addBinBtn = document.getElementById('addBinBtn');
    const resetDemoBtn = document.getElementById('resetDemoBtn');
    const saveBinBtn = document.getElementById('saveBinBtn');
    const binForm = document.getElementById('binForm');
    
    let allBins = []; // Keep a reference to check uniqueness
    
    addBinBtn.addEventListener('click', () => {
        binForm.reset();
        document.getElementById('binId').value = '';
        document.getElementById('binModalLabel').innerText = 'Add Bin';
        binModal.show();
    });
    
    resetDemoBtn.addEventListener('click', async () => {
        if(confirm('Are you sure you want to reset all data back to the demo state?')) {
            resetDemoBtn.innerHTML = '<i class="fa-solid fa-spinner fa-spin me-1"></i> Resetting...';
            resetDemoBtn.disabled = true;
            try {
                const res = await fetch('/api/bins/reset-demo', { method: 'POST' });
                if (res.ok) {
                    await initDashboard();
                } else {
                    alert('Failed to reset demo data');
                }
            } catch (err) {
                console.error(err);
                alert('Error resetting data');
            } finally {
                resetDemoBtn.innerHTML = '<i class="fa-solid fa-rotate-left me-1"></i> Reset Demo Data';
                resetDemoBtn.disabled = false;
            }
        }
    });
    
    saveBinBtn.addEventListener('click', async () => {
        if (!binForm.checkValidity()) {
            binForm.reportValidity();
            return;
        }
        
        const binId = document.getElementById('binId').value;
        const binCode = document.getElementById('binCode').value;
        
        // Uniqueness check for Bin Code
        const exists = allBins.find(b => b.binCode === binCode && b.id != binId);
        if (exists) {
            alert('Bin Code must be unique!');
            return;
        }
        
        const binData = {
            binCode: binCode,
            locationName: document.getElementById('locationName').value,
            latitude: parseFloat(document.getElementById('latitude').value),
            longitude: parseFloat(document.getElementById('longitude').value),
            capacity: parseFloat(document.getElementById('capacity').value),
            currentFillPercentage: parseFloat(document.getElementById('currentFillPercentage').value)
        };
        
        saveBinBtn.innerHTML = 'Saving...';
        saveBinBtn.disabled = true;
        
        try {
            const url = binId ? `/api/bins/${binId}` : '/api/bins';
            const method = binId ? 'PUT' : 'POST';
            
            const res = await fetch(url, {
                method: method,
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(binData)
            });
            
            if (res.ok) {
                binModal.hide();
                await initDashboard();
            } else {
                alert('Failed to save bin');
            }
        } catch (err) {
            console.error(err);
            alert('Error saving bin');
        } finally {
            saveBinBtn.innerHTML = 'Save Bin';
            saveBinBtn.disabled = false;
        }
    });
    
    // Attach to global window to allow onclick from table
    window.editBin = function(id) {
        const bin = allBins.find(b => b.id === id);
        if (bin) {
            document.getElementById('binId').value = bin.id;
            document.getElementById('binCode').value = bin.binCode;
            document.getElementById('locationName').value = bin.locationName;
            document.getElementById('latitude').value = bin.latitude;
            document.getElementById('longitude').value = bin.longitude;
            document.getElementById('capacity').value = bin.capacity;
            document.getElementById('currentFillPercentage').value = bin.currentFillPercentage;
            
            document.getElementById('binModalLabel').innerText = 'Edit Bin';
            binModal.show();
        }
    };
    
    window.deleteBin = async function(id) {
        if (confirm('Are you sure you want to delete this bin?')) {
            try {
                const res = await fetch(`/api/bins/${id}`, { method: 'DELETE' });
                if (res.ok) {
                    await initDashboard();
                } else {
                    alert('Failed to delete bin');
                }
            } catch (err) {
                console.error(err);
                alert('Error deleting bin');
            }
        }
    };

    async function initDashboard() {
        await loadPredictions();
        await loadSustainabilityComparison();
    }

    async function loadPredictions() {
        try {
            const response = await fetch('/api/predictions');
            const predictions = await response.json();
            
            await updateTableAndKPIs(predictions);
            updateStatusChart(predictions);
            updateFillChart(predictions);
            populateTrendSelector(predictions);
            
            // Auto-load trend for first bin if none selected
            if (!trendBinSelector.value && predictions.length > 0) {
                trendBinSelector.value = predictions[0].binId;
                loadTrendData(predictions[0].binId);
            }
        } catch (error) {
            console.error('Error fetching predictions:', error);
        }
    }

    async function updateTableAndKPIs(predictions) {
        const tbody = document.querySelector('#binsTable tbody');
        tbody.innerHTML = '';
        
        let totalBins = predictions.length;
        let urgentBinsCount = 0;
        
        // Fetch full bin info to have actual IDs and names
        try {
            const res = await fetch('/api/bins');
            allBins = await res.json();
        } catch(err) {
            console.error("Could not fetch bins list", err);
        }
        
        predictions.forEach(p => {
            const binData = allBins.find(b => b.id === p.binId) || {};
            const binCode = binData.binCode || `Bin ${p.binId}`;
            const locName = binData.locationName || `Location ${p.binId}`;
            
            if (p.recommendedStatus === 'URGENT' || p.recommendedStatus === 'OVERFLOW_RISK') {
                urgentBinsCount++;
            }
            
            const tr = document.createElement('tr');
            
            // Format hours
            const hoursStr = p.estimatedHoursToFull ? p.estimatedHoursToFull.toFixed(1) + 'h' : '-';
            
            // Format status badge
            let badgeClass = 'badge-normal';
            if (p.recommendedStatus === 'SOON') badgeClass = 'badge-soon';
            if (p.recommendedStatus === 'URGENT') badgeClass = 'badge-urgent';
            if (p.recommendedStatus === 'OVERFLOW_RISK') badgeClass = 'badge-overflow';
            
            tr.innerHTML = `
                <td class="fw-bold">${binCode}</td>
                <td>${locName}</td>
                <td>
                    <div class="d-flex align-items-center">
                        <span class="me-2">${p.currentFillPercentage.toFixed(1)}%</span>
                        <div class="progress flex-grow-1" style="height: 6px;">
                            <div class="progress-bar ${p.currentFillPercentage > 80 ? 'bg-danger' : 'bg-success'}" 
                                 style="width: ${p.currentFillPercentage}%"></div>
                        </div>
                    </div>
                </td>
                <td>${p.predictedFillPercentage.toFixed(1)}%</td>
                <td>${hoursStr}</td>
                <td><span class="badge ${badgeClass}">${p.recommendedStatus}</span></td>
                <td class="text-muted small">Just now</td>
                <td>
                    <button class="btn btn-sm btn-outline-primary py-0 px-2" onclick="editBin(${p.binId})" title="Edit">
                        <i class="fa-solid fa-pen"></i>
                    </button>
                    <button class="btn btn-sm btn-outline-danger py-0 px-2 ms-1" onclick="deleteBin(${p.binId})" title="Delete">
                        <i class="fa-solid fa-trash"></i>
                    </button>
                </td>
            `;
            tbody.appendChild(tr);
        });
        
        document.getElementById('kpi-total-bins').innerText = totalBins;
        document.getElementById('kpi-urgent-bins').innerText = urgentBinsCount;
    }

    function populateTrendSelector(predictions) {
        const currentVal = trendBinSelector.value;
        trendBinSelector.innerHTML = '<option value="">Select a bin...</option>';
        predictions.forEach(p => {
            const binData = allBins.find(b => b.id === p.binId) || {};
            const binCode = binData.binCode || `Bin ${p.binId}`;
            
            const opt = document.createElement('option');
            opt.value = p.binId;
            opt.innerText = binCode;
            trendBinSelector.appendChild(opt);
        });
        if (currentVal) {
            trendBinSelector.value = currentVal;
        }
    }

    function updateStatusChart(predictions) {
        const counts = { 'NORMAL': 0, 'SOON': 0, 'URGENT': 0, 'OVERFLOW_RISK': 0 };
        predictions.forEach(p => {
            if (counts[p.recommendedStatus] !== undefined) {
                counts[p.recommendedStatus]++;
            }
        });
        
        const data = {
            labels: ['Normal', 'Soon', 'Urgent', 'Overflow Risk'],
            datasets: [{
                data: [counts['NORMAL'], counts['SOON'], counts['URGENT'], counts['OVERFLOW_RISK']],
                backgroundColor: ['#198754', '#ffc107', '#dc3545', '#8b0000']
            }]
        };
        
        if (statusChart) {
            statusChart.data = data;
            statusChart.update();
        } else {
            const ctx = document.getElementById('statusChart').getContext('2d');
            statusChart = new Chart(ctx, {
                type: 'doughnut',
                data: data,
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: { legend: { position: 'right' } }
                }
            });
        }
    }

    function updateFillChart(predictions) {
        const labels = predictions.map(p => {
            const binData = allBins.find(b => b.id === p.binId) || {};
            return binData.binCode || `Bin ${p.binId}`;
        });
        const currentData = predictions.map(p => p.currentFillPercentage);
        const predictedData = predictions.map(p => p.predictedFillPercentage);
        
        const data = {
            labels: labels,
            datasets: [
                {
                    label: 'Current Fill (%)',
                    data: currentData,
                    backgroundColor: '#0d6efd'
                },
                {
                    label: 'Predicted Next 24h (%)',
                    data: predictedData,
                    backgroundColor: '#adb5bd'
                }
            ]
        };
        
        if (fillChart) {
            fillChart.data = data;
            fillChart.update();
        } else {
            const ctx = document.getElementById('fillChart').getContext('2d');
            fillChart = new Chart(ctx, {
                type: 'bar',
                data: data,
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    scales: { y: { beginAtZero: true, max: 100 } }
                }
            });
        }
    }

    async function loadTrendData(binId) {
        try {
            const response = await fetch(`/api/bins/${binId}/history`);
            const history = await response.json();
            
            const labels = history.map(h => new Date(h.timestamp).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'}));
            const fillData = history.map(h => h.fillPercentage);
            
            // Reverse so oldest is on the left
            labels.reverse();
            fillData.reverse();
            
            const data = {
                labels: labels,
                datasets: [{
                    label: `Fill Level`,
                    data: fillData,
                    borderColor: '#2e7d32',
                    backgroundColor: 'rgba(46, 125, 50, 0.1)',
                    fill: true,
                    tension: 0.4
                }]
            };
            
            if (trendChart) {
                trendChart.data = data;
                trendChart.update();
            } else {
                const ctx = document.getElementById('trendChart').getContext('2d');
                trendChart = new Chart(ctx, {
                    type: 'line',
                    data: data,
                    options: {
                        responsive: true,
                        maintainAspectRatio: false,
                        scales: { y: { beginAtZero: true, max: 100 } }
                    }
                });
            }
        } catch (error) {
            console.error('Error fetching trend data:', error);
        }
    }

    async function loadSustainabilityComparison() {
        try {
            const response = await fetch('/api/metrics/comparison');
            const metrics = await response.json();
            
            // Update KPIs
        document.getElementById('kpi-co2-saved').innerText = (metrics.baseline.estimatedCo2Kg - metrics.optimized.estimatedCo2Kg).toFixed(2) + ' kg';
        document.getElementById('kpi-route-distance').innerText = metrics.optimized.totalDistanceKm.toFixed(2) + ' km';
            
            const data = {
                labels: ['Fixed Schedule', 'Optimized Route'],
                datasets: [
                    {
                        label: 'Distance (km)',
                        data: [metrics.baseline.totalDistanceKm, metrics.optimized.totalDistanceKm],
                        backgroundColor: '#6c757d'
                    },
                    {
                        label: 'Fuel (L)',
                        data: [metrics.baseline.estimatedFuelLitres, metrics.optimized.estimatedFuelLitres],
                        backgroundColor: '#ffc107'
                    },
                    {
                        label: 'CO2 Emissions (kg)',
                        data: [metrics.baseline.estimatedCo2Kg, metrics.optimized.estimatedCo2Kg],
                        backgroundColor: '#2e7d32'
                    }
                ]
            };
            
            if (sustainabilityChart) {
                sustainabilityChart.data = data;
                sustainabilityChart.update();
            } else {
                const ctx = document.getElementById('sustainabilityChart').getContext('2d');
                sustainabilityChart = new Chart(ctx, {
                    type: 'bar',
                    data: data,
                    options: {
                        responsive: true,
                        maintainAspectRatio: false
                    }
                });
            }
        } catch (error) {
            console.error('Error fetching comparison metrics:', error);
        }
    }

    async function generateOptimizedRoute() {
        const btn = document.getElementById('generateRouteBtn');
        btn.innerHTML = '<i class="fa-solid fa-spinner fa-spin me-1"></i> Generating...';
        btn.disabled = true;
        
        try {
            const response = await fetch('/api/routes/optimize', { method: 'POST' });
            if (!response.ok) {
                throw new Error('Server returned ' + response.status);
            }
            
            const text = await response.text();
            let route = null;
            if (text) {
                route = JSON.parse(text);
            }
            
            if (route && route.stops && route.stops.length > 0) {
                document.getElementById('routeEmptyState').classList.add('d-none');
                document.getElementById('routeContent').classList.remove('d-none');
                    
                    document.getElementById('routeTotalDistance').innerText = route.totalDistanceKm.toFixed(2) + ' km';
                    document.getElementById('routeTotalFuel').innerText = route.estimatedFuelLitres.toFixed(2) + ' L';
                    document.getElementById('routeTotalCo2').innerText = route.estimatedCo2Kg.toFixed(2) + ' kg';
                    document.getElementById('kpi-route-distance').innerText = route.totalDistanceKm.toFixed(2) + ' km';
                    
                    const seqList = document.getElementById('routeSequenceList');
                    seqList.innerHTML = '';
                    
                    route.stops.forEach((stop, index) => {
                        const li = document.createElement('li');
                        li.className = 'list-group-item d-flex justify-content-between align-items-center';
                        li.innerHTML = `
                            <div>
                                <span class="badge bg-secondary rounded-pill me-2">${index + 1}</span>
                                <span class="fw-bold">${stop.binCode}</span>
                                <div class="text-muted small ms-4">${stop.locationName || 'Unknown Location'}</div>
                            </div>
                        `;
                        seqList.appendChild(li);
                    });
                } else {
                    document.getElementById('routeEmptyState').classList.remove('d-none');
                    document.getElementById('routeContent').classList.add('d-none');
                    document.getElementById('routeEmptyState').innerHTML = '<i class="fa-solid fa-check-circle fa-3x mb-3 text-success"></i><p class="mb-0">No urgent bins found. No collection needed.</p>';
                }
                
                // Refresh comparison data as well
                await loadSustainabilityComparison();
        } catch (error) {
            console.error('Error optimizing route:', error);
            alert('Failed to generate route.');
        } finally {
            btn.innerHTML = '<i class="fa-solid fa-route me-1"></i> Generate';
            btn.disabled = false;
        }
    }
});
