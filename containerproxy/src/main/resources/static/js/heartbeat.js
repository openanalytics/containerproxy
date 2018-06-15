function heartbeat(proxyId, rate) {
	setTimeout(function() {
		$.post("/api/heartbeat/" + proxyId, function(data) {
			heartbeat(proxyId, rate);
		})
	}, rate);
};