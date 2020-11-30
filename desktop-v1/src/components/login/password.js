// @flow
import React, {Component, PropTypes} from 'react';
import firebase from 'firebase';

require('./index.scss');

export default class Password extends Component {
  static propTypes = {
    setPassword: PropTypes.func
  };

  state: {
    errorMessage: string;
  };

  constructor (props) {
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

  handleEnterPress (e) {
    if (e.key === 'Enter') {
      this.handlePassword();
    }
  }

  handlePassword () {
    if (!this._password) {
      return;
    }
    const password = this._password.value;
    if (!password) {
      this.setState({ errorMessage: 'Invalid input' });
      return;
    }

    const auth = firebase.auth();
    return auth.signInWithEmailAndPassword(auth.currentUser.email, password)
      .then(() => {
        this.props.setPassword(password);
      })
      .catch((err) => {
        console.error('Error with password', err);
        let errorMessage = 'Invalid credentials.';
        switch (err.code) {
          case 'auth/wrong-password':
            errorMessage = 'Incorrect password.';
            break;
        }
        this.setState({errorMessage});
      });

    // return this.props.handlePassword(password);
  }

  render () {
    // TODO: add a splashy logo here or something
    return (
      <div className="password-container">
        {this.state.errorMessage && <div className="message">{this.state.errorMessage}</div>}
        <input
          ref={(ref) => {
            if (ref) {
              this._password = ref;
            }
          }}
          placeholder="Password"
          type="password" />
        <button onClick={this.handlePassword.bind(this)}>Login</button>
      </div>
    );
  }

}
