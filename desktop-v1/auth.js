import {parse} from 'url';
import {remote} from 'electron';
import qs from 'qs';

const GOOGLE_AUTHORIZATION_URL = 'https://accounts.google.com/o/oauth2/v2/auth';
const GOOGLE_TOKEN_URL = 'https://www.googleapis.com/oauth2/v4/token';
const GOOGLE_PROFILE_URL = 'https://www.googleapis.com/userinfo/v2/me';
const GOOGLE_CLIENT_ID = 'redacted  ';
const GOOGLE_REDIRECT_URI = 'redacted';

export async function googleSignIn () {
  console.debug('Starting Google auth');
  try {
    const code = await signInWithPopup();
    const tokens = await fetchAccessTokens(code);
    const {id, email, name} = await fetchGoogleProfile(tokens.access_token);
    console.debug('Successful Google auth');
    return {
      uid: id,
      email,
      displayName: name,
      idToken: tokens.id_token,
    };
  } catch (err) {
    console.error('Failed Google auth', err);
  }
}

function signInWithPopup () {
  return new Promise((resolve, reject) => {
    const authWindow = new remote.BrowserWindow({
      width: 500,
      height: 600,
      show: true,
    });

    // TODO: Generate and validate PKCE code_challenge value
    const urlParams = {
      response_type: 'code',
      redirect_uri: GOOGLE_REDIRECT_URI,
      client_id: GOOGLE_CLIENT_ID,
      scope: 'profile email',
    };
    const authUrl = `${GOOGLE_AUTHORIZATION_URL}?${qs.stringify(urlParams)}`;

    function handleNavigation (url) {
      const query = parse(url, true).query;
      if (query) {
        if (query.error) {
          reject(new Error(`There was an error: ${query.error}`))
        } else if (query.code) {
          // Login is complete
          authWindow.removeAllListeners('closed');
          setImmediate(() => authWindow.close());

          // This is the authorization code we need to request tokens
          resolve(query.code)
        }
      }
    }

    authWindow.on('closed', () => {
      // TODO: Handle this smoothly
      throw new Error('Auth window was closed by user')
    });

    authWindow.webContents.on('will-navigate', (event, url) => {
      handleNavigation(url)
    });

    authWindow.webContents.on('did-get-redirect-request', (event, oldUrl, newUrl) => {
      handleNavigation(newUrl)
    });

    authWindow.loadURL(authUrl)
  })
}

async function fetchAccessTokens (code) {
  const req = new Request(GOOGLE_TOKEN_URL, {
    method: 'POST',
    body: qs.stringify({
      code,
      client_id: GOOGLE_CLIENT_ID,
      redirect_uri: GOOGLE_REDIRECT_URI,
      grant_type: 'authorization_code',
    }),
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    }
  });

  const response = await fetch(req);
  return await response.json()
}

async function fetchGoogleProfile (accessToken) {
  const req = new Request(GOOGLE_PROFILE_URL, {
    method: 'GET',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${accessToken}`,
    },
  });

  const response = await fetch(req);
  return await response.json();
}
