/**
 * @flow
 * Created by nick on 11/26/16.
 */
import React from 'react';

/**
 * Returns an <img>/<div> element that either displays the recipient's photo, an acronym of their name, or "?"
 * @param recipient
 * @param i
 * @param isLayered
 * @returns {XML}
 */
export default (recipient: {name: string, photoUri: string}, i: number, isLayered: boolean) => {
  const layeredClass = isLayered ? 'layered' : '';
  if (recipient && recipient.photoUri) {
    return <img
      key={i}
      className={`recipient-avatar photo ${layeredClass}`}
      src={`data:image/jpeg;base64,${recipient.photoUri}`}/>;
  } else if (recipient && recipient.name && !parseInt(recipient.name)) {
    let abbrv;
    const split = recipient.name.split(' ');
    if (split.length > 1) {
      abbrv = split[0][0] + split[split.length - 1][0];
    } else {
      abbrv = split[0][0];
    }
    abbrv = abbrv.toUpperCase();
    return <div
      key={i}
      className={`recipient-avatar name ${layeredClass}`}>{abbrv}</div>;
  } else {
    return <div
      key={i}
      className={`recipient-avatar default ${layeredClass}`}>?</div>;
  }
};
