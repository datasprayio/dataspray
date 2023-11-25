
/** @type {import('next').NextConfig} */
const nextConfig = {
  ...(process.env.NEXTJS_OUTPUT && {
    output: process.env.NEXTJS_OUTPUT,
  }),
  trailingSlash: true,
  reactStrictMode: true,
  pageExtensions: ['ts', 'tsx', 'js', 'jsx', 'md', 'mdx'],
  transpilePackages: [
    '@cloudscape-design/components',
    '@cloudscape-design/component-toolkit'
  ]
};

module.exports = nextConfig;
