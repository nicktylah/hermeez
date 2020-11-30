// @flow
import React, {Component, PropTypes} from 'react';

require('./index.scss');

export default class Login extends Component {
  static propTypes = {
    login: PropTypes.func,
    loginWithGoogle: PropTypes.func
  };

  _email: Object;
  _password: Object;
  boundHandleEnterPress: Function;

  state: {
    errorMessage: string;
  };

  constructor (props: Object) {
    super(props);
    this.state = {
      errorMessage: ''
    };
    this.boundHandleEnterPress = this.handleEnterPress.bind(this);
    document.addEventListener('keydown', this.boundHandleEnterPress);
  }

  componentWillUnmount () {
    document.removeEventListener('keydown', this.boundHandleEnterPress);
  }

  handleEnterPress (e: Object) {
    if (e.key === 'Enter') {
      this.handleLogin();
    }
  }

  handleLogin () {
    if (!this._email || !this._password) {
      return;
    }
    const email = this._email.value;
    const password = this._password.value;
    if (!email || !password) {
      this.setState({ errorMessage: 'Invalid input' });
      return;
    }

    return this.props.login(email, password)
      .catch((err) => {
        console.error(err);
        let errorMessage = 'Invalid credentials.';
        switch (err.code) {
          case 'auth/invalid-email':
            errorMessage = 'Not a valid email address.';
            break;
          case 'auth/user-not-found':
            errorMessage = 'Could not find a user for that email address.';
            break;
          case 'auth/wrong-password':
            errorMessage = 'Incorrect password. Have you set up a password for this user?';
            break;
        }
        this.setState({ errorMessage: errorMessage });
      });
  }

  render () {
    // TODO: add a splashy logo here or something
    return (
      <div className="login-container">
        {this.state.errorMessage && <div className="message">{this.state.errorMessage}</div>}
        <input
          ref={(ref) => {
            if (ref) {
              this._email = ref;
            }
          }}
          placeholder="Email" />
        <input
          ref={(ref) => {
            if (ref) {
              this._password = ref;
            }
          }}
          placeholder="Password"
          type="password" />
        <button onClick={this.handleLogin.bind(this)}>Login</button>
        <div
          className="google-login"
          onClick={this.props.loginWithGoogle}>
          <img src="assets/images/btn_google_light_normal_ios.svg" className="google-logo"/>
          <div className="google-login-text">Sign in with Google</div>
        </div>
      </div>
    );
  }

}
