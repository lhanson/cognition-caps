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
        console.log('getloginstatus:', response);
        if (response.status === 'connected') {
            FB.api('/me', function(response) {
                console.log('Your user ID: ' + response.id);
            });
            $('.fb-login-button, #fb-logout-wrapper').toggle()
        }
        else if (response.status === 'not_authorized') {
            // User logged into Facebook but not authorized
            console.log('logged into FB but not authorized');
        } else {
            // User not logged in
            console.log('not logged in');
        }
        FB.Event.subscribe('auth.login', function(response) {
            console.log('got auth.login event', response);
            // Post our facebook-id so we can open a logged-in session
            var f = $('<form method="post" action="/skullbong/login"></form>');
            f.html('<input type="hidden" name="fb-id" value="' + response.authResponse.userID + '" />' +
                   '<input type="hidden" name="fb-access-token" value="' + response.authResponse.accessToken + '" />');
            f.appendTo($('body'));
            f.submit();
        });
    });
};

(function(d, debug){
    var js, id = 'facebook-jssdk', ref = d.getElementsByTagName('script')[0];
    if (d.getElementById(id)) {return;}
    js = d.createElement('script'); js.id = id; js.async = true;
    js.src = "//connect.facebook.net/en_US/all" + (debug ? "/debug" : "") + ".js";
    ref.parentNode.insertBefore(js, ref);
 }(document, false));

// Google Plus
(function() {
    var po = document.createElement('script'); po.type = 'text/javascript'; po.async = true;
    po.src = 'https://apis.google.com/js/plusone.js';
    var s = document.getElementsByTagName('script')[0]; s.parentNode.insertBefore(po, s);
})();
