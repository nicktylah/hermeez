// @flow
export default (
  promiseFunctionToRetry: Function,
  options: { initialBackoff?: number, retries?: number}): Promise<any> => {
  console.debug(promiseFunctionToRetry, options);
  return new Promise((resolve, reject) => {
    let currentBackoff = options.initialBackoff || 1000;
    let backoffStep = currentBackoff;
    let stepCount = 0;

    const retry = () => {
      stepCount++;
      promiseFunctionToRetry()
        .then(resolve, function (e) {
          if (options.retries && stepCount === options.retries) {
            return reject(e);
          }

          setTimeout(retry, currentBackoff);
          const temp = currentBackoff;
          currentBackoff += backoffStep;
          backoffStep = temp;
        });
    };

    retry();
  });
};

