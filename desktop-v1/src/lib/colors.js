/**
 * @flow
 * Author: Nick Tyler
 */
export const colors: Array<string> = [
  '#C41915',
  '#CA4318',
  '#EC6C20',
  '#FD8A26',
  '#9ACA28',
  '#C8B654',
  '#C79C38',
  '#CA7620',
  '#87B043',
  '#53A527',
  '#67971B',
  '#13874B',
  '#20AA66',
  '#66B393',
  '#1897A6',
  '#70B3B8',
  '#3CB6E3',
  '#199ACA',
  '#1993E7',
  '#1789CE',
  '#8266C9',
  '#754CB2',
  '#6568C9',
  '#1A57B6',
  '#A969CA',
  '#983BCA',
  '#9D44B6',
  '#FC464B',
  '#A29497',
  '#A37C82',
  '#C35E7E',
  '#E62565',
  '#424242',
  '#333333'
];

export function getRandomColor(): string {
  return colors[Math.floor(Math.random() * colors.length)];
}
