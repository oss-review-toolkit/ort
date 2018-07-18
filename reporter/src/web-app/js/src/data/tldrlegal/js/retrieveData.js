const request = require('request');
const fs = require('fs');


let page = 0;
let licenses = [];

const formatLicense = value => ({
    id: value.id,
    title: value.title,
    shorthand: value.shorthand,
    manager: {
        id: value.manager.id,
        username: value.manager.username,
    },
    slug: value.slug,
    source: `https://tldrlegal.com/api/license/${value.id}`,
    description: value.modules.summary && value.modules.summary.text,
    summary: {
        must: value.modules.summary
            && value.modules.summary.must
            && value.modules.summary.must.map(item => (
                {
                    title: item.attribute.title,
                    description: item.attribute.description,
                }
            )),
        cannot: value.modules.summary
            && value.modules.summary.cannot
            && value.modules.summary.cannot.map(item => (
                {
                    title: item.attribute.title,
                    description: item.attribute.description,
                }
            )),
        can: value.modules.summary
            && value.modules.summary.can
            && value.modules.summary.can.map(item => (
                {
                    title: item.attribute.title,
                    description: item.attribute.description,
                }
            )),
    },
    fulltext: value.modules.fulltext && value.modules.fulltext.text,
});

const formatLicenses = value => ({
    id: value.id,
    title: value.title,
    shorthand: value.shorthand,
    slug: value.slug,
    tags: value.tags,
    modules: value.modules,
});

const getLicense = async (id, callback) => {
    request(`https://tldrlegal.com/api/license/${id}`, (error, response) => {
        if (!error && response.statusCode === 200) {
            const resp = JSON.parse(response.body);
            const result = formatLicense(resp);
            const name = result.id;
            if (name) {
                fs.open(`./src/data/tldrlegal/js/result/${name}.json`, 'w', (err, fd) => {
                    if (fd) {
                        fs.write(fd, JSON.stringify(result, null, 2), () => {});
                        if (callback) {
                            callback();
                        }
                    }
                });
            } else {
                licenses = [...licenses.map((item) => {
                    if (item.id === id) {
                        return {
                            ...item,
                            error: true,
                        };
                    }
                    return item;
                })];
            }
        }
    });
};

const getLicenses = resolve => request(`https://tldrlegal.com/api/license?page=${page}`, (error, response) => {
    if (!error && response.statusCode === 200) {
        const array = JSON.parse(response.body);
        if (array.length) {
            licenses = [...licenses, ...array.map(formatLicenses)];
            page += 1;
            getLicenses(resolve);
        } else {
            licenses.forEach((item, i) => {
                getLicense(item.id, (i === licenses.length - 1 ? () => resolve(licenses) : null));
            });
        }
    }
});

const generateIndex = (values) => {
    const result = `${values.filter(item => !item.error).map(item => `import md${item.id} from './result/${item.id}.json';\n`).join('')}
const data = [${values.map((item) => {
        const licenceName = `md${item.id}`;
        return `\n\t\t{
        ${Object.keys(item).map(key => `${key}: ${JSON.stringify(item[key])}, `).join('\n\t\t\t\t')}
        license: ${licenceName},
    }`;
    })}\n];\n
export const metadata = {
    packageName: 'tldrlegal.com',
    packageHomePage: 'https://tldrlegal.com',
    packageCopyrightText: 'Copyright © 2012-2017 FOSSA, Inc. All rights reserved. ',
}
export default data;`;

    fs.open('./src/data/tldrlegal/js/index.js', 'w', (err, fd) => {
        fs.write(fd, result, () => {});
    });
};

const start = async () => new Promise((resolve) => {
    getLicenses(resolve);
});


start().then((resp) => {
    generateIndex(resp);
});
