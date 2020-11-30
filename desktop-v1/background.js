chrome.app.runtime.onLaunched.addListener(function() {
  chrome.app.window.create('src/container.html', {
    id: 'main',
    outerBounds: {
      'width': 770,
      'height': 490
    }
  });
});

chrome.runtime.onMessage.addListener(function(message) {
  switch (message.action) {
    case 'NEW_MESSAGE_RECEIVED':
      chrome.notifications.create(null, {
        type: 'basic',
        title: 'New message from ' + message.meta.sender,
        iconUrl: 'data:image/jpeg;base64,' + message.meta.photo,
        message: message.meta.message
      });
      break;
  }
});
