// @flow
import * as React from 'react';

import Notification from './notification';
import style from '../app.scss';

type Props = {
  notifications: Array<{
    id: number,
    message: string,
    type?: 'error' | 'success'
  }>,
  onNotificationTimeout: (id: number) => void
};

export default class Notifications extends React.Component<Props> {
  render() {
    const { props } = this;

    return (
      <div className={style.notificationsCont}>
        {props.notifications.map((notification, i) => {
          let color = '#F8FEEF';
          if (notification.type === 'error') color = '#FEEFEF';

          return (
            <Notification
              key={i}
              color={color}
              id={notification.id}
              index={i}
              message={notification.message}
              onNotificationTimeout={props.onNotificationTimeout}
            />
          );
        })}
      </div>
    );
  }
}
