// @flow
import * as React from 'react';
import style from '../app.scss';

type Props = {
  color: string,
  id: number,
  index: number,
  message: string,
  onNotificationTimeout: Function
};

type State = { active: boolean };

export default class Notification extends React.PureComponent<Props, State> {
  state = { active: false };

  componentDidMount() {
    const { props } = this;
    this.setState({ active: true }, () => {
      setTimeout(
        props.onNotificationTimeout.bind(null, props.id),
        4000 + props.index * 1000
      );
    });
  }

  render() {
    return (
      <div
        className={style.notification}
        style={{
          background: this.props.color
        }}
      >
        {this.props.message}
      </div>
    );
  }
}
