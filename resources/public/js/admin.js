// Ignore console on platforms where it is not available
if (typeof(window.console) == "undefined") {
    console = {};
    console.log = console.warn = console.error = function(a) {};
}

// Facebook
window.fbAsyncInit = function() {
    $('#fb-logout').click(function() {
        FB.logout(function(response) {
            console.log('User is now logged out');
            var f = $('<form method="post" action="/skullbong/logout"></form>');
            f.appendTo($('body'));
            f.submit();
        });
    });
    FB.init({
      appId      : '460323184018046',
      channelUrl : '//www.wearcognition.com/channel.html',
      status     : false,
      cookie     : true,
      xfbml      : true
    });
    // Additional init code here
    FB.getLoginStatus(function(response) {
        function cognitionLogin(userID, accessToken) {
            // Post our facebook-id so we can open a logged-in session
            var f = $('<form method="post" action="/skullbong/login"></form>');
            f.html('<input type="hidden" name="fb-id" value="' + userID + '" />' +
                   '<input type="hidden" name="fb-access-token" value="' + accessToken + '" />');
            f.appendTo($('body'));
            f.submit();
        }
        if (response.status === 'connected') {
            // Logged into Facebook
            if (!$('body').hasClass('logged-in')) {
                // Need to tell the server what Facebook user we are
                FB.api('/me', function(meResponse) {
                    cognitionLogin(response.authResponse.userID, response.authResponse.accessToken);
                });
            }
            $('.fb-login-button, #fb-logout-wrapper').toggle();
        }
        else if (response.status === 'not_authorized') {
            console.log('Logged into Facebook but not authorized');
        } else {
            console.log('User is not logged into Facebook');
        }
        FB.Event.subscribe('auth.login', function(response) {
            console.log('Got Facebook auth.login event', response);
            cognitionLogin(response.authResponse.userID, response.authResponse.accessToken);
        });
    });
};

