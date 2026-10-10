const modern = require('simple-git-modern')
module.exports = Object.assign(modern.simpleGit, modern, { default: modern.simpleGit })
