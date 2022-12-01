const {appendWebpackPlugin} = require('@rescripts/utilities')
const {editWebpackPlugin} = require('@rescripts/utilities')
const fs = require('fs');
const fse = require('fs-extra');
const HTMLInlineCSSWebpackPlugin = require('html-inline-css-webpack-plugin').default;
const WebpackEventPlugin = require('webpack-event-plugin');

module.exports = {
  webpack: config => {
    if (process.env.NODE_ENV !== 'development') {
      const configA = editWebpackPlugin(
        p => {
          p.inlineSource = '.(js|css)$';
          p.chunks = ['chunk'];
          p.title = 'ORT WebApp Scan Report';

          return p
        },
        'HtmlWebpackPlugin',
        config,
      );

      const configB = editWebpackPlugin(
        p => {
          p.tests = [/.*/];
          return p
        },
        'InlineChunkHtmlPlugin',
        configA,
      );

      const configC = appendWebpackPlugin(
        new HTMLInlineCSSWebpackPlugin(),
        configA,
      );

      const configD = appendWebpackPlugin(
        new WebpackEventPlugin([{
          hook: 'done',
          callback: (compilation) => {
            console.log('Removing unneeded files in build dir...');

            fse.remove('./build/static')
              .catch(err => {
                console.error(err);
              });

            fse.remove('./build/asset-manifest.json')
              .catch(err => {
                console.error(err);
              });

            fs.readdir('./build', (err, files) => {
              for (let i = files.length - 1; i >= 0; i -= 1) {
                let match = files[i].match(/precache-manifest.*.js/);
                if (match !== null)
                  fse.remove(`./build/${match[0]}`)
                    .catch(err => {
                      console.error(err);
                    });
              }
            });

            fse.remove('./build/service-worker.js')
              .catch(err => {
                console.error(err);
              });

            console.log('Creating ORT template file...');

            fs.readFile('./build/index.html', 'utf8', (err, data) => {
              if (err) {
                return console.log(err);
              }

              const result = data.replace(
                /(<script type=")(.*)(" id="ort-report-data">)([\s\S]*?)(<\/script>)/,
                '$1application/gzip$3ORT_REPORT_DATA_PLACEHOLDER$5'
              );

              fs.writeFile('./build/scan-report-template.html', result, 'utf8', (err) => {
                if (err) return console.log(err);
              });
            });
          }
        }]),
        configC,
      )

      return configD;
    }

    return config;
  }
};
